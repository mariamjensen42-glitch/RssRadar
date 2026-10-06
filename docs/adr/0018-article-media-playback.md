# 正文媒体播放：直链视频/音频内嵌播放，第三方页面仍只给占位卡

阅读页正文里的 `<video>` / `<audio>` **直链文件**可以在页内直接播放：视频就地出画面，
音频是紧凑播放条；订阅源级（enclosure）视频另有底栏入口，打开全屏播放页。
`iframe` 这类第三方页面维持原判：只给「媒体占位卡」，点击外跳。

## Status

accepted

## Context

正文里的媒体此前只有一种处理：净化阶段 `sanitizeHtml` 把 `iframe`/`video` 替换成
`<a class="media-card">` 占位卡，点击经 `openUrl` 跳到浏览器（CONTEXT.md「媒体占位卡」）。
那条约束的**真实动机**是「正文里永不执行第三方页面脚本」——不引入 WebView JS、不动 ADR-0007
的视口渲染与内存约束。它针对的是第三方页面，不是媒体文件本身。

两处缺口：

1. **`<audio>` 被静默丢弃**。`audio` 既不在 `sanitizeHtml` 的删除列表里，也没像 `video`
   那样被替换成占位卡；随后 `cleanAttributes` 只白名单 `a`/`img` 的地址属性，`audio@src`
   被剥掉，残留的空标签在解析端拿不到地址，整体消失。播客类源的正文音频因此完全不可见。
2. **视频只能外跳**。ReadYou 差距表第 20 项一直挂着「均非嵌入播放」。而 `video@src`
   指向的是 mp4/webm 这类**直链文件**，App 侧解码不需要第三方页面，也不需要 JS——
   拿"不执行第三方脚本"去挡它，是把约束用错了地方。

能力上零缺口：`core:playback` 已有 Media3（app 侧连 `media3-ui-compose` 都是现成依赖），
而直链文件的播放与第三方嵌入播放是两件完全不同的事。

## Decision

### 1. 按"是不是第三方页面"分流，不按标签名分流

- `iframe` / `object` / `embed` → **媒体占位卡**（原样保留），只外跳。
- `video` / `audio`（拿得到 http(s) 直链）→ 占位卡，但记下媒体类型
  （`media-card-video` / `media-card-audio` 类名后缀），渲染层据此换成内嵌播放器。
- 判不出来（无类型后缀、地址是相对路径、非 http 协议）→ 退回占位卡。
  **默认值取安全侧**：判不出类型时宁可只给外跳卡，也不给一个点不动的播放键。

类型走后缀类名而不是 `data-*` 属性，是因为 `cleanAttributes` 的属性白名单是"声明级"的，
加一个属性就要再开一个口子；类名本来就在白名单里（WebView 路的 CSS 也在用 `.media-card`），
扩展它不增加新的攻击面。白名单按**令牌逐字重建**（`media-card` + 至多一个已知类型后缀），
不用 `startsWith` 放行——否则 `class="media-card evil"` 会把 `evil` 一起带出去。

**但净化发生在入库前**（`RssParser` / `ArticleExtractor` 都把产物写进 `article.content`），
于是库里大量是**加后缀之前**的行，它们只有 `class="media-card"`。只认类名后缀的话，
存量文章的视频永远停在"点了跳浏览器"（实测就是这么暴露出来的）。因此类型判定是三级、
先到先得：类名后缀 → 卡片文案前缀（旧数据唯一的类型线索，自家文案就是「视频 · 域名」
/「嵌入内容 · 域名」，而那时音频压根没产出卡片，不会误判）→ URL 后缀（去 query/fragment
再比，覆盖 CDN 直链）。三级都判不出 ⇒ `EMBED`，即安全侧。
`<iframe src=".../clip.mp4">` 这类**直接指向媒体文件**的嵌入也按 URL 后缀放行——加载的是文件
而不是第三方页面，不触碰约束。

### 2. 播放器：阅读页一个实例，延迟创建，页面内生命周期

`ArticleMediaPlayer`（feature:article）持有**唯一**一个 `ExoPlayer`：

- **延迟创建**：没人点播放就不建。整页扫描下来最坏几十个媒体节点，人手一个播放器
  就是几十套解码器与音频通道。
- **一次只播一条**：切媒体复用同一实例，切换时 `setMediaItem` 重新 prepare。
- **页面内生命周期**：组合销毁即 `release()`；App 退到后台（ON_STOP）暂停。
  换篇即 `stop()`。

视频画面用 media3 官方的 `PlayerSurface`（Compose 原生），不是
`AndroidView { PlayerView }`：与正文同一套组合模型，尺寸随 `videoSize` 变化由 Compose 重排。

**不复用 `core:playback` 的 `PlaybackController`**：那条链是**后台播客**语义
（MediaSession + 前台服务 + 通知栏，退出播放页继续响，见 ADR 里 audio 那条链）。
正文媒体是页面内的一次性播放，退到后台就该停——两者生命周期相反，共用一套只会互相打架
（把正文里的视频接进 MediaSession，锁屏后它还在响而画面早没了）。

### 3. 入口与瞬时 UI

- 正文卡内：视频点击起播、播放中显控制条（进度/播放键/时间/全屏/外部打开），
  音频是紧凑条 + 播放时才展开的进度。
- 底栏新增视频入口（`mediaKind == MEDIA_KIND_VIDEO` 且有地址），与既有的音频入口并列；
  它覆盖的是**订阅源级 enclosure 视频**——那段地址不在正文 HTML 里，正文区没有它的位置。
- 全屏播放页是阅读页之上的**瞬时 UI**（`Dialog`，与全屏看图 `ReaderImagePage` 同一套），
  不进导航路由。
- **「退出全屏」与「终止播放」是两件事**，按"正文里有没有这条媒体的画面"分：
  有（正文视频卡）⇒ 只是退出全屏，退回正文继续播；没有（订阅源级视频不在正文 HTML 里，
  全屏页是它唯一的画面）⇒ 停掉，否则留一段"看不见的声音"。正文视频卡在组合期向
  `ArticleMediaPlayer` 登记自己的 URL（`attachInlineRenderer`），退出时据此判定。
  这个差别也决定左上角按钮的语义与长相（`Minimize2` + 「退出全屏」 vs `X` + 「关闭」）——
  长得像"关闭"、按下去却继续播的按钮是在骗人。
- 全屏请求挂在播放器状态上（`fullscreenUrl`），而不是一路透传回调穿过
  `RenderNode` → `NativeNodesColumn` → `BodyContent` → `ReadingBody` → Screen 五层。

## Considered Options

- **A. 只做外跳（维持现状）**：否决。直链媒体本来就能在 App 内解，外跳是能力缺失而非克制。
- **B. 放进 WebView 播（开启 JS / 允许 iframe）**：否决。那正是 ADR-0007 与
  CONTEXT.md「媒体占位卡」要挡的东西——第三方脚本、整页 WebView 的内存与栅格代价。
- **C. 复用后台播放链（MediaSession）**：否决。见上，生命周期语义相反；且会让"阅读页里的
  一段音频"变成必须走前台服务与通知栏的播客。
- **D. 每条媒体各自一个 ExoPlayer**：否决。资源与复杂度都随节点数线性膨胀。
- **E. 统一走独立播放页（正文卡点击 → 全屏页）**：部分采纳。正文内嵌是主路径
  （读者不必离开正文），全屏页留给订阅源级视频与"想看大屏"的补充路径。

## Consequences

- `sanitizeHtml` 的 `<audio>` 分支是**行为变更**：此前正文音频完全不出现，现在会出现一张卡。
  但**净化在入库前**，所以这条只对之后新抓取的文章生效；存量行的音频卡片不会凭空出现
  （它们那会儿连标签都没留下），而**视频**靠上面三级判定能在存量行上直接生效。
- WebView 渲染路（opt-in）不受影响：那边的 `media-card` 仍是外跳链接，CSS 选择器
  `.media-card` 对带后缀的类名照样命中。原生路才是默认渲染器，因此该功能默认可见。
- 双语对照下媒体卡从原文侧剥掉（`stripVisualDuplicates`）：它是**有状态的播放单元**，
  同一张卡出现两次就是两个播放器抢同一个实例。
- 新增依赖：`feature:article` 引入 `media3-common` / `media3-exoplayer` / `media3-ui-compose`。
- 失败必可见：播放错误显示本地化文案 + media3 的 `errorCodeName`（留给排查），
  出路是播放键（重试）与外部打开，不静默。
- **一个 `Player` 只有一个视频输出**：全屏页与正文卡**不能同时**挂 `PlayerSurface`。
  media3 的 `PlayerSurface` 在 dispose 时并不清除输出，attach 走的是只含
  `(view, player)` 的 `LaunchedEffect`——所以两个同时挂时输出留在后挂的那个（全屏页的 View），
  全屏页一关那个 View 就销毁了，而正文卡的 `LaunchedEffect` 键值没变、不会重跑去重新挂。
  症状是"声音还在、画面永远停在最后一帧"。做法：`fullscreenUrl == node.url` 期间正文卡不挂画面，
  让全屏页独占输出；关闭后正文卡重新组合、自然接管。**加任何新的播放画面时都要记住这条**。
