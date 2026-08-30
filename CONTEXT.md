# RssRadar

一个以 RSSHub 为核心的 Android RSS 阅读器：订阅源经解析后入库，供列表浏览、检索与详情阅读。

## Language

**订阅源（Feed）**:
用户订阅的一个内容来源地址。可以由 RSSHub 路由构建，也可以是站点自带的标准地址。
_Avoid_: 频道、channel、源地址、feed url

**文章（Article）**:
订阅源发布的一条内容，是列表展示与详情阅读的基本单位。
_Avoid_: 条目、item、entry、帖子、文章项

**摘要（Summary）**:
文章的短文本版本，供列表浏览与检索使用。长度有限，不保证完整。
_Avoid_: 描述、description、简介、正文摘要

**正文（Content）**:
文章的完整内容，供详情页阅读。可能由订阅源直接提供，也可能在用户真正要读时才从原网页取得。
_Avoid_: 全文、内容、body、文章主体

**按需抓取（On-demand fetch）**:
只在用户打开某篇文章时才去获取它的正文，而不是在订阅时预先抓取全部内容。
_Avoid_: 预取、预加载、后台抓取

**RSSHub 路由（Route）**:
RSSHub 中一段带参数的路径模板。填入参数后得到一个具体的订阅源地址。
_Avoid_: 规则、规则表、路径模板、route path

**路由目录（Route catalog）**:
应用可检索的 RSSHub 路由全量集合，来自官方路由元数据（docs.rsshub.app/routes.json）。随包内置一份精简快照，可联网更新；更新后的缓存优先于内置快照。支持关键词搜索、分类筛选与热度排序。
_Avoid_: 路由表、路由市场、路由库

**目录快照（Catalog snapshot）**:
随包发布的路由目录数据（assets/rsshub-routes.json，slim schema），保证首次安装离线可用。随版本更新，不保证与官方最新路由一致。
_Avoid_: 内置路由、离线包、预置数据

**命名空间（Namespace）**:
路由元数据里的一级分组，通常对应一个站点或平台（如 `bilibili`）。一个命名空间下挂若干路由，命名空间自身带站点名、域名、分类与热度。
_Avoid_: 源、分组、目录、namespace key

**路由示例（Route example）**:
一条已填好参数、可直接订阅的路由路径。优先取实例上真实被订阅的 feed（带标题），否则退回官方文档给出的 example。用户点一下即反填参数并预览。
_Avoid_: 样例、模板值、demo 链接

**路由热度（Route heat）**:
RSSHub 统计的路由订阅量级。作为目录默认排序依据与「热门」角标阈值，不对外显示为具体数字。
_Avoid_: 权重、流行度、score

**实例（Instance）**:
提供 RSSHub 路由解析服务的站点地址。同一个路由在不同实例上都能解析。
_Avoid_: 服务器、节点、镜像源

**内容状态（Content state）**:
文章身上来自订阅源的那些属性：标题、时间、摘要、正文。刷新订阅源时允许被更新。
_Avoid_: 文章字段、元数据

**用户状态（User state）**:
读者对一篇文章的标记：已读、收藏、稍后读。只由用户操作改变，**刷新订阅源永不覆盖**。
_Avoid_: 阅读状态、标记、flag

**AI 摘要（AI summary）**:
由大语言模型基于文章正文生成的内容概括，展示在详情页标题下的常驻卡片。一经生成持久保存，刷新订阅源永不覆盖。
_Avoid_: 智能摘要、TL;DR、自动摘要

**AI 翻译（AI translation）**:
由大语言模型把文章正文整体译为简体中文的替换式译文：译文替换正文显示，可随时切回原文。不持久化，仅在会话内缓存。
_Avoid_: 双语对照、机翻、智能翻译

**替换式翻译（Replacement translation）**:
AI 翻译的呈现方式：正文整体换成译文，与原文之间一键切换。不做段落级双语对照。
_Avoid_: 对照模式、双语模式

**订阅源清单（OPML）**:
跨阅读器交换订阅源及其分组结构的标准 XML 格式。文件夹嵌套表达分组，多级层级用 `/` 拼接。
_Avoid_: 备份文件、outline 列表、订阅导出

**媒体占位卡（Media placeholder card）**:
正文里嵌入媒体（iframe / video）净化后的替身：一张带域名和播放图标的卡片，点击跳原站播放页。不是嵌入播放——正文里永不执行第三方页面脚本。
_Avoid_: 视频卡、占位符、内嵌播放器

**站点图标（Site icon）**:
代表订阅源所属站点的图形标识，抓取自源站点（feed XML 里的站点链接，而非 RSSHub 实例本身）。以远程 URL 形式持久保存；仅在尚未取得时抓取，一经写入不再覆盖；显示失败时回落字母占位。
_Avoid_: favicon、头像、logo、icon

**站点链接（Site link）**:
订阅源 XML 中指向源站点首页的链接，是站点图标的抓取目标。来自 feed 的 channel/根元素，与订阅源地址（可能是 RSSHub 路由地址）不同。
_Avoid_: 主页链接、htmlUrl、官网

**盲导（Blind import）**:
导入订阅源清单时不联网校验、不抓取内容，直接入库；文章由导入后的定向刷新补齐。OPML 自带的标题与分组原样保留。
_Avoid_: 批量添加、批量校验、静默导入

**列表显示项（List display options）**:
信息流与订阅源文章列表卡片上可逐项开关的展示元素：订阅源图标、订阅源名称、日期、缩略图、描述档位，外加粘性日期头与已读弱化两个开关。全局一份设置，即改即见；默认值等于无配置时的渲染。
_Avoid_: 显示设置、列表样式、布局选项

**粘性日期头（Sticky date header）**:
列表按自然日分组后吸附在顶部的日期标签。只做视觉分组，不改变排序与分页；无发布日期的文章不参与分组。
_Avoid_: 分组头、日期分组、section header

**已读弱化（Read dimming）**:
可选的已读态视觉：已读文章的标题与摘要降为弱色。未读文章的呈现永不因此改变。
_Avoid_: 已读进度、阅读进度、灰化

**自动同步（Auto sync）**:
由系统调度器按用户设定周期在后台刷新订阅源的机制；完成后按归档保留策略清理到期文章。手动刷新是独立的另一条路径，永不受自动同步的任何约束（网络、充电、屏蔽）限制。
_Avoid_: 后台刷新、定时刷新、云同步

**归档（Archive）**:
文章到期真删（非标记隐藏）的存储维护策略：以发布时间为基准（缺失时回落抓取时间），发布时间早于保留期限的文章被清理，收藏与稍后读的文章永不清理。清理在打开应用时和自动同步完成后执行。
_Avoid_: 清空、过期删除、隐藏、保留天数

**同步屏蔽（Sync opt-out）**:
单个订阅源退出自动同步的开关；屏蔽后该源不参与后台刷新，手动刷新照常可用。
_Avoid_: 黑名单、屏蔽清单、禁用订阅

## Navigation

**路由（Route）**:
一个屏幕或浮层的类型安全标识（Kotlin `@Serializable` 对象或数据类），作为导航图中的一个目的地。
_Avoid_: 页面、screen、destination id、路径

**目的地（Destination）**:
导航图中由某个 Route 声明、可被导航到的可组合项。
_Avoid_: 页面、screen

**导航图（NavGraph）**:
应用所有 Route 及其关系的集中声明，定义于 `NavHost`。
_Avoid_: 路由表

**底部栏（Bottom bar）**:
主界面底部 4 个顶层目的地的切换栏；选中态由当前 Route 决定。
_Avoid_: 底部导航、tab bar、导航条

**返回栈（Back stack）**:
`NavController` 维护的目的地栈，驱动系统返回与预测性返回。
_Avoid_: 历史栈

**预测性返回（Predictive back）**:
Android 13+ 上系统返回手势的预览动画，由导航库内置支持。
_Avoid_: 边缘返回、手势返回

**深链接（Deep link）**:
通过 URI（如 `rssradar://article/{id}`）直接导航到某目的地的机制。
_Avoid_: 外部链接、web link

**订阅源文章列表（Feed article list）**:
单一订阅源的全部文章的浏览页：按时间新→旧排列、分页加载，从订阅源清单点击订阅源进入。不带信息流的过滤 tab 与分组筛选；订阅源的管理操作仍在清单行尾的入口。
_Avoid_: 源详情、feed 详情页、源内页

## Dependency Injection

**Hilt**:
官方依赖注入框架，接管原手写 `AppContainer`；通过 `@HiltAndroidApp`、`@AndroidEntryPoint`、`@HiltViewModel` 标注接入。
_Avoid_: Dagger、Koin、AppContainer

**模块（Module）**:
用 `@Module @InstallIn` 声明、提供依赖（Room 数据库、解析器等）的 Hilt 装配类。
_Avoid_: 容器、provider、component

**作用域 ViewModel（Scoped ViewModel）**:
绑定到某个导航图作用域、在该图内多个目的地间共享的 ViewModel（如加订阅两步流）。
_Avoid_: 共享 VM、图级 VM

## State Management (MVI)

**意图（Intent）**:
封装一次用户动作或一次性触发（如切换分组、提交订阅、标记已读、消费提示）的不可变值，用 per-ViewModel 的 `sealed interface XxxIntent` 表达，经单一 `onIntent(intent)` 进入 ViewModel。
_Avoid_: 事件、action、动作、命令、command

**一次性效应（Effect）**:
只应发生一次、不应留在界面状态里的副作用（如弹出 Snackbar、导航）。计划由候选 B 引入专用通道；当前 `uiMessage` 仍置于 `UiState` 内、由 `Intent.ConsumeMessage` 消费。
_Avoid_: 事件、SideEffect

**界面状态（UiState）**:
驱动界面渲染的不可变快照。当前仅 AddSubscription / Search 已聚合为单一 `UiState`；其余 VM 仍是碎片 `StateFlow`，由候选 C 统一。
_Avoid_: state、视图状态
