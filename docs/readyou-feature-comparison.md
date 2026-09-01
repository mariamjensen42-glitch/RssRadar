# ReadYou vs RssRadar 功能对比

> 对比对象：`D:\Programming\Kotlin\ReadYou-main`（重构后的新版结构）vs RssRadar。
> 生成时间：2026-08-30。

## RssRadar 已有功能（不列入差距）

分组管理（GroupStore 注册表）、All/未读/星标/稍后读四个 tab、分组筛选、全局搜索、
信息流分页 + 下拉刷新、抓取原文（readability4j + 缓存 + 静默降级）、封面图（enclosure→media:*→正文首图）、
阅读时长估算、多主题 + 深色跟随系统（ThemeStore + RssRadarPalette）、
阅读排版（字号/行距/边距/字体族，ReadingStyleStore）、OPML 导入、RSSHub 路由抽屉（14 条内置路由 + 参数化订阅）。

## ReadYou 有、RssRadar 没有的功能

### 一、同步生态（最大差距）

| # | 功能 | ReadYou 依据 |
|---|------|--------------|
| 1 | Fever / Google Reader / FreshRSS 账号同步 | `domain/service/*RssService.kt`、`infrastructure/rss/provider/` |
| 2 | 多账号支持（可添加多个不同类型账号） | `domain/service/AccountService.kt` |
| 3 | 同步策略：间隔、仅 WiFi、仅充电、启动时同步、按 feed 屏蔽 | `infrastructure/preference/Sync*Preference.kt` |

### 二、订阅源管理

> 2026-09-01 实施记录（#5 Feed 自动发现）：
> - `FeedDiscovery`（纯函数）：从站点 HTML 提取 `<link rel=alternate>` 的 feed 候选（只认 feed 类 MIME，
>   排除 `application/xml` 这种会被 sitemap 污染的类型），相对地址按 baseUrl 解析为绝对地址，去重保序上限 8 条。
> - `FeedRepository.discoverFeeds()` 三步降级：地址本身是 feed → 直接返回；否则抓 HTML 取声明的候选 → 逐个
>   **真抓取真解析**验证；站点没声明 → 试常见路径（/feed、/rss.xml、/atom.xml…）再验证。
> - 加订阅抽屉：手填地址校验失败即触发发现，候选列表（标题 + 地址 + 真实文章数）点一条即采用并重新校验。
> - 原则：只返回真能解析出文章的候选，不猜、不返回没验证过的地址。

| # | 功能 | ReadYou 依据 | 状态 |
|---|------|--------------|------|
| 4 | OPML **导出**（RssRadar 只有导入） | `domain/service/OpmlService.kt`（`saveToString`） | ✅ 2026-09-01 |
| 5 | Feed 自动发现（输入网址自动探测 feed 链接） | `infrastructure/rss/RssHelper.kt`（`discoverFeedLink`） | ✅ 2026-09-01 |
| 6 | Favicon 自动抓取（Besticon 服务） | `infrastructure/rss/BestIconFinder.kt`（RssRadar 有 iconUrl 字段但无抓取链路） | ✅ 2026-08-30 |
| 7 | 批量移动 feed 到分组 | `drawer/group/AllMoveToGroupDialog.kt` | ✅ 2026-08-31 |
| 8 | 清空 feed / 分组文章 | `ClearFeedDialog.kt`、`ClearGroupDialog.kt` | ✅ 2026-08-31 |
| 9 | Feed 级预设：单独开关通知、单独开关全文抓取 | `ui/page/home/feeds/drawer/feed/FeedOptionDrawer.kt` | ⚠️ 部分 |

> 订阅源管理三项实施记录（2026-08-31）：
> - **#7 批量移动**：订阅管理页顶栏入口进入多选态 → 勾选 → 选目标分组一次写库（`FeedDao.updateGroupForFeeds`）。
> - **#8 清空**：`ArticleDao.deleteByFeed` / `deleteByGroup` 真删文章、保留订阅源；收藏与稍后读豁免（与归档同一豁免规则）。
>   入口：订阅操作页「清空文章（保留订阅）」、分组操作底栏「清空分组文章」，均二次确认，提示语汇报真实删除数与保留数。
> - **#9 Feed 级预设**：只做了**全文抓取开关**（`feeds.fullContentEnabled`，DB v7），`FeedRepository.fetchFullContent` 读它决定要不要抓原网页。
>   **通知开关没做**——应用零通知代码（见第六节 #31），加了字段与开关也没有读取方，等于假功能。等通知系统落地再补这一项。

### 三、信息流体验

> 2026-09-01 实施记录（#4 / #10 / #11）：
> - **#4 OPML 导出**：`OpmlWriter`（纯函数，分组路径 `技术/后端` 还原成嵌套 outline）+ `FeedRepository.exportOpml()`，
>   订阅管理页顶栏导出图标走 SAF 另存为（`rssradar-subscriptions-yyyyMMdd.opml`）。往返测试覆盖导入→导出→再导入。
> - **#10 标记已读条件**：`MarkAsReadCondition`（1/3/7 天前/全部，时间基准 `COALESCE(publishedAt, fetchedAt)`，
>   与归档清理一致）；信息流顶栏 CheckCheck 入口 + 通用档位弹层；数字来自 DAO 真实影响行数。
> - **#11 滚动时自动标记已读**：`ListDisplayState.markReadOnScroll`（默认关——会改用户数据，必须显式选择）。
>   列表按"槽位表"（粘性日期头占 null 槽）算出滚出视口顶部的卡片，批量写库；卡片状态走快照翻转，
>   未读 tab 下不当场消失，避免滚动时列表在脚下抽掉。设置页「列表显示」内开关。


| # | 功能 | ReadYou 依据 |
|---|------|--------------|
| 10 | 标记已读条件（1/3/7 天前或全部） | `domain/model/general/MarkAsReadConditions.kt` | ✅ 2026-09-01 |
| 11 | 滚动时自动标记已读（可关） | `MarkAsReadOnScrollPreference.kt` | ✅ 2026-09-01 |
| 12 | 列表显示项逐项可配：feed 图标/名称、日期、缩略图、描述、粘性日期头、已读进度指示 | `FlowArticleList*Preference.kt` |
| 13 | 文章归档策略（保留天数） | `KeepArchivedPreference.kt` |
| 14 | 未读排序方式可配 | `SortUnreadItemsPreference.kt` |

### 四、阅读页（ReadYou 最重的部分）

| # | 功能 | ReadYou 依据 |
|---|------|--------------|
| 15 | 双渲染器：WebView 或原生 Compose 二选一（RssRadar 只有 styled-HTML 一条路） | `ReadingRendererPreference.kt`、`ui/component/webview/`、`ui/component/reader/` |
| 16 | 4 种阅读主题：Material You / Reeder / Paper / 自定义 | `ReadingThemePreference.kt` |
| 17 | 排版细项：标题/小标题对齐+加粗+大写、字间距、正文对齐（RssRadar 只有字号/行距/边距/字体族四项） | `ReadingText*Preference.kt`、`ReadingTitle*Preference.kt` |
| 18 | 粗体字符强调（类 Bionic Reading） | `ReadingBoldCharactersPreference.kt` |
| 19 | 图片圆角、图片最大化、图片全屏查看页 | `ReadingImage*Preference.kt`、`ReaderImagePage.kt` |
| 20 | 视频/iframe 嵌入播放（YouTube） | `ui/component/reader/VideoTagHunter.kt` |
| 21 | TTS 朗读 | `ui/page/home/reading/tts/TtsButton.kt` |
| 22 | 沉浸模式（工具栏自动隐藏） | `ReadingAutoHideToolbarPreference.kt` |
| 23 | 手势：下拉/上拉切换上/下篇、列表条目左右滑动自定义动作、下拉加载下一个 feed | `PullToSwitchArticlePreference.kt`、`ui/component/swipe/`、`PullToLoadNextFeedPreference.kt` |
| 24 | 大屏/平板双栏自适应（列表+阅读同屏） | `ui/page/adaptive/` |
| 25 | 自定义字体导入（TTF） | `ui/ext/ExternalFonts.kt` |
| 26 | 分享内容格式可配、链接打开方式可配（Custom Tabs/指定浏览器/询问） | `SharedContentPreference.kt`、`OpenLinkPreference.kt` |

### 五、主题

| # | 功能 | ReadYou 依据 |
|---|------|--------------|
| 27 | Material You 动态取色（Monet） | `ui/theme/palette/` |
| 28 | AMOLED 纯黑 | `AmoledDarkThemePreference.kt` |
| 29 | 自定义主色 | `CustomPrimaryColorPreference.kt` |
| 30 | Feeds/Flow/Reading 三区独立主题预览 | `ui/page/settings/color/` |

### 六、系统级

| # | 功能 | ReadYou 依据 |
|---|------|--------------|
| 31 | 新文章系统通知（含渠道分组、Feed 级开关）——RssRadar 零 notification 代码 | `infrastructure/android/NotificationHelper.kt` |
| 32 | 桌面小部件（文章卡片 + 列表两种，带配置页） | `ui/widget/ArticleCardWidget.kt`、`ArticleListWidget.kt` |
| 33 | 应用内多语言切换 | `ui/page/settings/languages/` |
| 34 | 系统分享/文本选择/翻译 intent 接入 | `AndroidManifest.xml`（SEND / PROCESS_TEXT / TRANSLATE） |
| 35 | 应用内检查更新 | `NewVersionNumberPreference.kt`、`domain/service/AppService.kt` |
| 36 | 崩溃报告页、使用提示/疑难解答页 | `CrashReportActivity`、`ui/page/settings/tips|troubleshooting/` |

## 反向差距（RssRadar 独有，ReadYou 没有）

- RSSHub 路由市场/参数化订阅（ReadYou 无 RSSHub 概念）—— RssRadar 的核心差异化。
- 阅读时长估算。
- 稍后读成型（ReadYou 仅数据库字段 `isReadLater`，无 UI）。

## 取舍建议

### 必做——RSS 刚需，成本都不高

1. **OPML 导出**——导入链路已有，补个序列化就完事。没有导出 = 用户数据被绑架，不敢认真用。
2. **Feed 自动发现**——手填 URL 抽屉里输入任何网址都该能探测出 feed，订阅体验的下限。
3. **Favicon 自动抓取**——iconUrl 字段和 FeedIcon 组件都在，只差抓取链路。列表没图标像半成品。
4. **标记已读条件**（1/3/7 天前/全部）——积累几天未读就刷不完的信息流，用户会弃用。
5. **新文章通知 + Feed 级开关**——RSS 阅读器没有通知就只是个"偶尔打开看看的网页"（当前零 notification 代码，从 WorkManager + 通知渠道开始做）。

这五项一个迭代能做完，全是"没有就不像一个正经 RSS 阅读器"的东西。

### 最该做的一件大事——RSSHub 路由发现

当前只有 14 条内置路由，是硬伤。RSSHub 有几千条路由且带在线文档/搜索 API，接上它就免费获得
"feed 发现"能力——正好是 ReadYou 和其他所有阅读器都没有的。这是「RSSHub 为核心」定位该有的
样子，也是整个项目的护城河。附带把 RSSHub 实例地址做成可配置（现在写死 DEFAULT_HOST）。

### 可选——看想不想吃 AI 这张牌

AI 摘要/翻译属于"语言组织"，不违反"数字必须真实"原则，且现有阅读器（含 ReadYou）都没做好
中文 RSS 的 AI 摘要。建议等前面六件做完再动。

### 明确不建议

- 第三方同步生态（Fever / Google Reader / FreshRSS）——工程量巨大，且和"本地 + RSSHub"定位打架
- 双渲染器、排版长尾设置（标题大写、字间距等）、双栏自适应、TTS、桌面小部件——设置平台化的坑，单人项目填不动

### 一句话结论

先把必做 5 项做成"完整的 RSS 阅读器"，然后 all-in RSSHub 路由发现。

## 附：ReadYou 自己也缺的功能（2026-08-30 调研结论）

- **同步**：Feedly / Inoreader 仅枚举占位零实现；Miniflux / TTRSS / Feedbin 连占位都没有；无自有云同步，纯本地账号无法跨设备。
- **内容**：无 AI 摘要/翻译；无稍后读服务集成（Wallabag/Pocket）；无内置翻译；无播客/音频支持（Spotify 计划未实现，音频 enclosure 不能播）；不支持 Epub 导入。
- **智能化**：无关键词过滤规则引擎（只有按 feed 屏蔽）；无相似文章去重；无 feed 发现/推荐；无搜索历史。
- **细节**：稍后读只有数据库字段无 UI（半成品）；无文章高亮标注；桌面小部件 README 自标未完成；无桌面端/多端。

其中 **AI 能力、过滤规则、feed 发现** RssRadar 也没有——是双方共同的空白区，也是差异化机会：
RssRadar 独有的 RSSHub 参数化订阅恰好填了"feed 发现"的洞，RSSHub 本身就是最强的 feed 生成器。
