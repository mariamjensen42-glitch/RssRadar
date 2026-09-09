# RssRadar 功能盘点（2026-09-09）

> 口径：**用户可见、可在 UI 上触发或配置的独立能力**，不含内部重构与性能优化。
> 计数依据 = 代码（`AiFeature` 枚举 / `Routes.kt` / `SettingsSubPages.kt` / 各 Store / 各 Screen），
> 不以宣传文案为准。合计 **128 项**。

## A. 订阅源与 RSSHub（24）

| # | 功能 | 代码依据 |
|---|------|---------|
| 1 | 手填 URL 添加订阅 | `AddSubscriptionSheet` |
| 2 | Feed 自动发现（HTML link 探测 + 常见路径试探 + 真抓取验证） | `FeedDiscovery` / `FeedRepository.discoverFeeds()` |
| 3 | RSSHub 路由目录（分类浏览 / 搜索） | `RouteCatalogStore` |
| 4 | RSSHub 参数化订阅 | `AddSubscriptionViewModel` |
| 5 | RSSHub 实例切换 | `RssHubInstanceStore` |
| 6 | 自定义实例地址 | 同上 |
| 7 | 内置镜像列表 | `docs/rsshub-instances.md` + 设置页 |
| 8 | OPML 导入（文件夹还原为分组） | `OpmlParser` |
| 9 | OPML 导出（SAF 另存，带日期文件名） | `OpmlWriter` |
| 10 | Favicon 自动抓取（Besticon） | `BestIconFinder` |
| 11 | 分组新建 / 重命名 / 删除 | `GroupStore` + 长按底栏 |
| 12 | 批量移动 feed 到分组 | `FeedDao.updateGroupForFeeds` |
| 13 | 批量删除 feed（二次确认） | `SubscriptionsScreen` 多选态 |
| 14 | 清空 feed 文章（保留订阅） | `ArticleDao.deleteByFeed` |
| 15 | 清空分组文章 | `ArticleDao.deleteByGroup` |
| 16 | Feed 级通知开关 | `feeds.notificationsEnabled`（DB v10） |
| 17 | Feed 级全文抓取开关 | `feeds.fullContentEnabled`（DB v7） |
| 18 | Feed 级摘要提示词覆盖 | `AiFeature.SUMMARY` / 提示词模板页 |
| 19 | 订阅源搜索（大库拍平检索） | `SubscriptionsViewModel` |
| 20 | 单源文章列表 | `FeedArticlesRoute` |
| 21 | 单源单独刷新 | `FeedArticlesViewModel` |
| 22 | 失效源检测（原因分类 / 连续计数 / 自动恢复） | `RefreshEngine` + `FeedHealthWorker` |
| 23 | 失效源筛选 + 一键删除（二次确认） | `SubscriptionsScreen` |
| 24 | 源类型标识（RSSHub / 普通） | FeedRow 角标 |

## B. 信息流（20）

| # | 功能 | 代码依据 |
|---|------|---------|
| 25 | 五个 tab：全部 / 未读 / 收藏 / 稍后读 / 推荐 | `FeedListScreen` |
| 26 | 推荐流（本地打分，可关） | ADR-0013 `Recommendation` |
| 27 | 内容类型筛选（收进弹层） | `ContentTypeFilter` |
| 28 | 分组筛选（下沉 DB 查询） | issue #74 |
| 29 | 分页加载（30 / 页） | `PagedSnapshot` |
| 30 | 下拉全量刷新 + n/N 进度 | `FeedListScreen` |
| 31 | 视图模式 4 种（列表 / 卡片 / 杂志 / 网格） | `ListViewMode` |
| 32 | 列表显示项 7 项可配（图标 / 名称 / 日期 / 缩略图 / 描述 / 粘性日期头 / 已读进度） | `ListDisplayStore`（issue #56） |
| 33 | 摘要行数 3 档（关 / 短 / 长） | `ListDescMode` |
| 34 | 已读灰显 | `dimRead` |
| 35 | 滚动自动标已读（默认关） | `markReadOnScroll` |
| 36 | 批量标记已读（1 / 3 / 7 天前 / 全部） | `MarkAsReadCondition` |
| 37 | 未读排序可配 | `FeedSortStore` |
| 38 | 左右滑手势（自研方向仲裁） | `ui/components` + 文档 |
| 39 | 长按菜单 10 项 | `ArticleContextMenu` |
| 40 | 删除撤销（Snackbar） | issue #46 |
| 41 | 「减少此类」负反馈 + 撤销 | ADR-0013 |
| 42 | 全局搜索 | `SearchRoute` |
| 43 | 搜索历史（存 / 删 / 清空） | `SearchViewModel.history` |
| 44 | 空态直达添加订阅 | 空态页 |

## C. 阅读页（22）

| # | 功能 | 代码依据 |
|---|------|---------|
| 45 | 双渲染器：原生 Compose / WebView | ADR-0009，默认原生 |
| 46 | 按需抓取全文 + 缓存 + 静默降级 | ADR-0012 `OnDemandFetch` |
| 47 | 正文 / 摘要切换（只在实质不同时给开关） | ADR-0016 `canSwitchToSummary` |
| 48 | 阅读主题 4 档（跟随 / 纸张 / 淡灰 / 夜间灰） | `ReadingThemeColors` |
| 49 | 排版 6 项（字号 / 行距 / 边距 / 字间距 / 字体族 / 正文对齐） | `ReadingPrefs` |
| 50 | 图片圆角 | `cornerRadius` |
| 51 | 图片全屏查看 | `ReaderImagePage` |
| 52 | 工具栏随滚动自动隐藏 | `AutoHideBars` |
| 53 | 顶栏标题补位（标题滚出视口才出现） | `ArticleDetailScreen` |
| 54 | 沉浸阅读（内容降噪） | `ReadingDenoise` |
| 55 | 上 / 下一篇 | 底栏导航组 |
| 56 | 越界下拉 / 上拉切篇（默认关） | `PullToSwitch` |
| 57 | 收藏 | 底栏 |
| 58 | 稍后读 | 底栏 |
| 59 | 查看原文 | `OpenUrl` |
| 60 | 分享（3 种格式可配） | `LinkStore.shareFormat` |
| 61 | 链接打开方式可配（浏览器 / 每次询问） | `LinkStore.linkOpenMode` |
| 62 | 阅读时长估算 | `core:model` 计算 |
| 63 | 封面图（enclosure → media:* → 正文首图） | 解析层 |
| 64 | 外部链接唤起 `rssradar://article/{id}` | Manifest intent-filter |
| 65 | 译文双语布局（对照 / 上下 + 仅译文切换） | `BilingualLayout` |
| 66 | MathML 公式渲染 | `MathMl.kt` |

## D. AI 智能功能（35）

`AiFeature` 枚举 1–35，分三组：内容处理 15 / 推荐发现 10 / 辅助推送 10。

**有专属入口或阅读页按钮（21）**：
SUMMARY(1) TRANSLATE(2) CLASSIFY(3) TAGS(4) SENTIMENT(5) KEYWORDS(6) OPINION(7) QA(8)
QUALITY(11) NOISE(12) OUTLINE(13) CREDIBILITY(14) GLOSSARY(15) PERSONAL_FEED(16)
RELATED(21) SHARE_COPY(27) HABIT(30) USAGE(33) TASK_QUEUE(34) PROMPT_TEMPLATE(35)

**只有「AI 产物中心」出口、无专属页（13）**：
FULLTEXT(9，未接线) DEDUPE(10) FEED_RECOMMEND(17) DISCOVER(18) TOPIC_GALAXY(19)
BUBBLE_BREAK(20) AGGREGATE(22) INTEREST_RANK(23) EVENT_MERGE(24) COLD_START(25)
DAILY_BRIEF(26) SMART_NOTIFY(28) FEED_HEALTH(29) DAILY_REPORT(31) FILTER_RULE(32)

> 注：上面第二组 15 项，其中 FULLTEXT 明确「不接线」、DISCOVER/TOPIC_GALAXY/COLD_START/
> FILTER_RULE 的 entry 注释写着「专属入口尚未实现」。它们**跑得通、结果看得到**，
> 但**没有自己的页面**。对外说「35 项 AI 功能」时这是必须如实标注的部分。

## E. 外观与主题（7）

| # | 功能 | 代码依据 |
|---|------|---------|
| 102 | 浅色 / 深色 / 跟随系统（深色即 AMOLED 纯黑） | `ThemeStore.mode` |
| 103 | Material You 动态取色（只换强调色，默认关，Android 12 以下禁用并说明） | #27 |
| 104 | 自定义主色（12 预设 + HSL 滑杆，前景色按 WCAG 自动选黑/白） | #29 |
| 105 | 动效体系 + 跟随系统「减弱动态效果」自动降级 | `docs/motion.md` |
| 106 | 状态栏图标跟随应用内主题 | issue #68 |
| 107 | 启动屏（不闪白） | issue #69 |
| 108 | 界面语言切换（跟随系统 / 中文 / English） | ADR-0017 `AppLocales` |

## F. 同步与系统（15）

| # | 功能 | 代码依据 |
|---|------|---------|
| 109 | 自动同步间隔可配 | `SyncStore.interval` |
| 110 | 仅 WiFi 同步 | `onlyOnWifi` |
| 111 | 仅充电时同步 | `onlyWhenCharging` |
| 112 | 启动时同步 | `syncOnStart` |
| 113 | 手动全量刷新 | 信息流下拉 |
| 114 | 32 路并发 + ETag/Last-Modified 条件请求（304 跳过） | DB v15 |
| 115 | 每日后台 AI 任务 | `AiDailyWorker` |
| 116 | 失效源每日自动复探 | `FeedHealthWorker` |
| 117 | 新文章通知（全局开关 + Feed 级 + Android 13 运行时权限） | `NotificationHelper` |
| 118 | 文章归档保留天数可配 | `ArchiveStore` |
| 119 | 墓碑式清理 + 收藏/稍后读豁免 | `ArticleCleaner` |
| 120 | 应用内检查更新（只查 release，不自动装包） | `UpdateCheckRow` |
| 121 | 崩溃日志（最近 5 份 + 全文导出） | `CrashLogRoute` |
| 122 | 系统分享 intent（ACTION_SEND） | ADR-0016 |
| 123 | 文本选择 intent（ACTION_PROCESS_TEXT） | ADR-0016 |

## G. 诊断与统计（5）

| # | 功能 | 代码依据 |
|---|------|---------|
| 124 | 全文抓取诊断页（按站点归因） | `FetchDiagnosticsRoute` |
| 125 | 兴趣画像页（只读，回答"为什么推荐这些"） | `InterestProfileRoute` |
| 126 | 阅读统计仪表盘（近 7 天真实数字） | `ReadingStatsRoute`（#83） |
| 127 | AI 产物中心（按功能摊开全部产物） | `AiArtifactsRoute` |
| 128 | 使用提示 / 技巧页 | `TipsScreen` |

## 一句话结论

**有，128 项，远超 100。** 其中 AI 一项就贡献 35 个。
但诚实的说法是：**约 114 项有直接入口，14 项 AI 功能只能去「AI 产物中心」看结果**——
数字可以报 128，前提是同时说明这 14 项没有专属页面。
