# ReadYou 解析与正文处理实现调查报告

> 源码版本：main 分支（2026-08 抓取，tarball 快照）。包根：`me.ash.reader`。
> 所有路径均相对于仓库根 `app/src/main/java/`。

## 1. Feed 解析：Rome 2.1.0

**库与版本**：`com.rometools:rome` + `rome-modules`，均为 **2.1.0**（`gradle/libs.versions.toml`，`app/build.gradle.kts:161-162`）。

**解析入口**：`infrastructure/rss/RssHelper.kt`

```kotlin
// RssHelper.kt:96
private fun parseFeed(body: ByteArray, httpContentType: String): SyndFeed =
    ByteArrayInputStream(body).use { inputStream ->
        SyndFeedInput().build(XmlReader(inputStream, httpContentType))
    }
```

`XmlReader(inputStream, contentType)` 处理 HTTP Content-Type 与 XML 内声明的编码探测。同步时 `isPreserveWireFeed = true`（RssHelper.kt:184）。

**SyndEntry → 数据模型转换**：`RssHelper.buildArticleFromSyndEntry()`（RssHelper.kt:198-235）

```kotlin
val desc = syndEntry.description?.value
val content =
    syndEntry.contents
        .takeIf { it.isNotEmpty() }
        ?.let { it.joinToString("\n") { it.value } }
        // contents = content:encoded (RSS2) / <content> (Atom)，多个用 "\n" 拼接

return Article(
    date = (syndEntry.publishedDate ?: syndEntry.updatedDate)?.takeIf { !it.isFuture(preDate) } ?: preDate,
    title = syndEntry.title.decodeHTML() ?: feed.name,
    author = syndEntry.author,
    rawDescription = content ?: desc ?: "",   // ← 全文字段优先级：content > description
    shortDescription = Readability.parseToText(desc ?: content, syndEntry.link).take(280),
    img = findThumbnail(syndEntry) ?: findThumbnail(content ?: desc),
    link = syndEntry.link ?: "",
)
```

**字段优先级结论**：
- 正文（`rawDescription`，存 DB）：**contents 优先，description 兜底**，二者都为空才存空串。
- 摘要（`shortDescription`，限 280 字符）：**description 优先，content 兜底**，经 Readability 提纯文本。
- 日期：`publishedDate` 兜底 `updatedDate`，再兜底同步时间；未来时间被 `isFuture` 过滤。
- 标题：`decodeHTML()` 解 HTML 实体，空则用 feed 名。

增量拉取用 `takeWhile { latestLink != it.link }` 截断到上次最新一条（RssHelper.kt:188）。

## 2. HTML 清洗：无白名单 Safelist，靠 Readability 提纯

**没有** Jsoup `Safelist.clean()` / 白名单过滤。清洗策略是"两段式"：
1. **摘要文本**：`Readability.parseToText()`（`infrastructure/html/Readability.kt:14`）→ `Readability4JExtended(uri, html).parse().textContent`，产出纯文本，天然无 XSS 面。
2. **正文 HTML**：全文解析走 `Readability.parseToElement()` 返回 Jsoup `Element`（Readability.kt:24-27），readability 算法本身就剔除了脚本/导航/无关标签；然后**原样输出 `element.toString()`** 存入缓存（RssHelper.kt:154-161）。渲染时原生组件只处理有限的标签类型（见 §4），WebView 渲染时靠 `reeder.html` 模板 + JS 接口，同样不做服务端白名单清洗。

另有一处小清洗：全文提取后若正文第一个 `<h1>` 与文章标题相同则移除（RssHelper.kt:156-159），避免标题重复。

```kotlin
// RssHelper.kt:154
val articleContent = Readability.parseToElement(content, link)
articleContent?.let {
    val h1Element = articleContent.selectFirst("h1")
    if (h1Element != null && h1Element.hasText() && h1Element.text() == title) h1Element.remove()
    articleContent.toString()
}
```

Jsoup 本体**未在 gradle 中显式声明**，是 `readability4j` 的传递依赖。

## 3. 全文抓取（"全文解析"）

**触发时机（三个）**：
1. **订阅时按需开关**：订阅对话框有 `isFullContent` 选项，存到 `Feed.isFullContent`（`domain/service/AbstractRssRepository.kt:47-64`，`domain/model/feed/Feed.kt:35`）。
2. **打开文章时按需**：`ui/page/adaptive/ArticleListReaderViewModel.kt:324-367`

```kotlin
suspend fun ReaderState.renderContent(articleWithFeed: ArticleWithFeed): ReaderState {
    return if (articleWithFeed.feed.isFullContent) {
        val fullContent = ...读缓存...
        if (fullContent != null) ReaderState.FullContent(fullContent)
        else { renderFullContent(); ReaderState.Loading }   // 缓存未命中 → 抓取
    } else ReaderState.Description(articleWithFeed.article.rawDescription)
}
```
   `renderFullContent()` 调 `readerCacheHelper.readOrFetchFullContent()`，>100ms 未完成则显示 Loading。
3. **同步后的后台 Worker**：`SyncWorker` 完成后链式入队 `ReaderWorker`（`domain/service/SyncWorker.kt:38-53`），并发 2：

```kotlin
// domain/service/ReaderWorker.kt:27-40
val articleList = rssService.queryUnreadFullContentArticles()  // DB: WHERE f.isFullContent = 1 AND 未读
articleList.map { async { semaphore.withPermit { cacheHelper.checkOrFetchFullContent(it) } } }
```

**存放位置：不是数据库，是文件缓存**。`infrastructure/rss/ReaderCacheHelper.kt`：`cacheDir/readability/<accountId>/SHA-256(articleId).html`（ReaderCacheHelper.kt:24-35, 37-50）。数据库 `Article.fullContent` 字段已标 `@Deprecated("fullContent is the same as rawDescription")`（`domain/model/article/Article.kt:34-35`）——旧版本存过 DB，新版本改为"rawDescription 存 feed 自带内容 + 全文抓取结果只进文件缓存"。

**正文提取算法**：readability4j 1.0.8（Mozilla Readability 的 Kotlin/Jsoup 移植）+ 自定义扩展：

```kotlin
// infrastructure/html/Readability.kt:29-42
Readability4JExtended(uri, html, options, RegExUtilExtended(),
    Preprocessor(...), MetadataParser(...),
    articleGrabber = RYArticleGrabberExtended(options, regExUtil),  // 自定义抓取器
    postprocessor = PostprocessorExtended())
```

`RYArticleGrabberExtended`（`infrastructure/html/RYArticleGrabberExtended.kt`）继承 `ArticleGrabberExtended` 重写 `prepareNodes`：按 class/id 正则移除 byline 与 unlikely candidate、删空容器、把"仅含单个 p 的 div"和"纯文本 div"转成 `<p>` 计分——即标准 readability 打分流程的调优版。

## 4. 详情页渲染：双渲染器，用户可选

`ui/page/home/reading/Content.kt:79-146` 按 `LocalReadingRenderer` 分支：

1. **WebView**（`ui/component/webview/RYWebView.kt`）：`AndroidView` 包 WebView，把 HTML 塞进本地模板 `reeder.html`（`ui/component/webview/WebViewHtml.kt`），Compose 侧的 Material 配色/字号/行高/圆角等以 CSS 变量注入；带 `JavaScriptInterface` 回传图片点击、`WebViewClient` 注入 Referer（`refererDomain`）防盗链。
2. **原生 Compose**（`ui/component/reader/Reader.kt` + `HtmlToComposable.kt`，代码移植自 Feeder 项目，GPL-3.0）：

```kotlin
// ui/component/reader/HtmlToComposable.kt:63-81
fun LazyListScope.htmlFormattedText(inputStream: InputStream, ...) {
    Jsoup.parse(inputStream, null, baseUrl)?.body()?.let { body ->
        formatBody(element = body, ...)   // 递归遍历 Jsoup DOM → Compose
    }
}
```
   自写 HTML→Compose 转换（非 AnnotatedString 单字符串，而是 **LazyColumn item 化**）：段落/标题聚合成 `AnnotatedString`（`TextComposer.kt`），`img` → Coil `rememberAsyncImagePainter`（`Image.kt:106`），代码块 → 横向滚动的 Surface，视频标签由 `VideoTagHunter.kt` 捕捉，链接用 `LinkAnnotation.Url` + 点击回调。

注意：`libs.compose-html`（`com.github.ireward:compose-html:1.0.2`）仅用于 StartupPage 的静态说明文本，**不用于正文渲染**。

## 5. 图片：Coil 2.5.0

依赖：`coil-base / coil-compose / coil-svg / coil-gif` 各 2.5.0 + `me.saket.telephoto:zoomable:0.15.1`（图片双指缩放，`app/build.gradle.kts:142-145,163`）。SVG 图标另配 `com.caverock:androidsvg-aar:1.4`。

**缩略图来源优先级**（`RssHelper.findThumbnail()`，RssHelper.kt:237-283）：
1. `syndEntry.enclosures.first().url`（Rome 解析的 enclosure）
2. media-rss 模块 `MediaEntryModule`：先 `metadata.thumbnail`，再 `medium == "image"` 的 `mediaContents`（RssHelper.kt:250-270）
3. 兜底：对 `content ?: desc` 的 HTML 字符串跑正则——先匹配 `<enclosure url="...">`，再取**第一个 `<img src>`**（排除 `data:` 内联 base64，防止大图撑爆 DB cursor；正则抄自 Feeder 项目）：

```kotlin
// RssHelper.kt:37-38
val enclosureRegex = """<enclosure\s+url="([^"]+)"\s+type=".*"\s*/>""".toRegex()
val imgRegex = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)
```

**不用 og:image**（og:image 属于网页而非 feed，只在抓全文时由 readability4j 的 MetadataParser 顺带解析但不用于缩略图）。feed 图标则由 `BestIconFinder` 抓站点域名找最优 icon（RssHelper.kt:285-292）。

## 6. 正文缓存策略

- **存文件不存 DB**：SHA-256(articleId) 命名，按账号分目录（`ReaderCacheHelper.kt`）。
- **读 > 写**：`readOrFetchFullContent` 先查文件，未命中才 `parseFullContent` 网络抓取并落盘（ReaderCacheHelper.kt:76-84）。
- **后台预热**：SyncWorker → ReaderWorker 只对 `isFullContent=1` 的 feed 的**未读**文章预热（并发 2，失败整体 retry）。
- **失效**：文章被归档清理时同步删缓存（SyncWorker.kt:31-34 调 `readerCacheHelper.deleteCacheFor`）；设置里有 `clearCache()` 全清。**没有基于时间的自动更新**——缓存命中即永远用缓存，除非被归档清理。

## 7. 依赖清单（解析/网络/图片/HTML 相关）

来自 `gradle/libs.versions.toml` + `app/build.gradle.kts`：

| 依赖 | 版本 | 用途 |
|---|---|---|
| com.rometools:rome / rome-modules | 2.1.0 | RSS/Atom 解析 + media-rss 模块 |
| net.dankito.readability4j | 1.0.8 | 正文提取（传递引入 Jsoup） |
| okhttp / okhttp-coroutines-jvm | 5.0.0-alpha.12 | 全部网络请求（feed、全文、图标） |
| io.coil-kt:coil-base/compose/svg/gif | 2.5.0 | 图片加载 |
| me.saket.telephoto:zoomable | 0.15.1 | 文章图片缩放 |
| be.ceau:opml-parser | 3.1.0 | OPML 导入导出 |
| com.squareup.retrofit2 (+gson) | 2.11.0 | 仅 Fever/GReader API 同步 |
| com.github.ireward:compose-html | 1.0.2 | 仅启动页静态 HTML 文本 |
| com.caverock:androidsvg-aar | 1.4 | SVG 图标 |

## 对 RssRadar 的启示

**值得直接抄的**：
1. **`buildArticleFromSyndEntry` 的字段优先级**：正文 `content > description`、摘要 `description > content`、日期 published→updated→now 且过滤未来时间——这套组合拳经过验证，直接照搬。
2. **缩略图三级 fallback**：enclosure → media-rss 模块 → 正文首个 `<img>`（排除 data: URI）。纯本地 RSS 拿不到更多来源，这套够了；我们若接 RSSHub，media-rss 字段很常见，第 2 级会高频命中。
3. **readability4j 做全文提取**：自写启发式不如它可靠，且它自带 Jsoup，不用额外引库。可抄 `RYArticleGrabberExtended` 的 prepareNodes 调优。
4. **全文抓取结果存文件缓存（SHA-256 命名）而非 DB 列**：避免大 HTML 撑爆 Room cursor，也绕开 DB 迁移。注意 280 字符摘要的 `take(280)` 做法可直接用。
5. **"首个 h1 与标题相同则删除"**：小细节但详情页观感差异大。
6. **编码探测**：Content-Type 无 charset 时 peek 读 `meta[http-equiv=content-type]`（RssHelper.kt:128-151），中文站点 GBK feed 常见，值得抄。

**场景差异要注意**：
1. **同步通道**：ReadYou 的 Rome 解析只服务于 Local 账号；Fever/FreshRSS/GReader 走 Retrofit+GSON API 拉结构化 JSON，解析层在 `FeverRssService`/`GoogleReaderRssService` 各写一份。RssRadar 纯本地 RSS+RSSHub，只需 Local 路径，`AbstractRssRepository` 的多账号抽象可以完全砍掉。
2. **无白名单清洗**：ReadYou 敢不做 Safelist 是因为渲染端（原生 Compose 组件只认有限标签 / WebView 模板）天然收敛了攻击面。RssRadar 若用 WebView 渲染，仍建议加 Jsoup Safelist 或至少 `document.select("script,iframe").remove()`，因为本地 RSS 的信任边界同样存在（恶意 feed）。
3. **缓存永不更新**：ReadYou 全文缓存命中即不刷新。若 RssRadar 用户期望"重新抓取最新正文"，需要加手动刷新入口（ReadYou 也没有，这是它的功能缺口而非设计必然）。
4. **`ReaderWorker` 预热限"未读"**：依赖它有"已读"语义和 `Feed.isFullContent` 按 feed 开关。RssRadar 若无此开关，可改为对摘要极短（如 <100 字符）的文章自动触发全文抓取。
5. **okhttp 5.0.0-alpha.12 是预发布版**：参考其用法即可，RssRadar 上 4.12/5.x stable 更稳妥。
