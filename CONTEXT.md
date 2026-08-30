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

**盲导（Blind import）**:
导入订阅源清单时不联网校验、不抓取内容，直接入库；文章由导入后的定向刷新补齐。OPML 自带的标题与分组原样保留。
_Avoid_: 批量添加、批量校验、静默导入

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
