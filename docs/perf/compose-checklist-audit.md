# Compose 单向数据流清单 · 落地审计

对照一份外部《Compose 单向数据流清单》审计 RssRadar 现状（315 个 Kotlin 文件），
记录「清单条目 → 项目现状 → 本次动作」，逐条可核销。

**日期**：2026-10-05 ｜ **验证**：`scripts/check-kotlin.py --main-only` = **0 error**（315 文件 / 4 warning）

---

## 一、逐条对照

### 1. 架构与导航

| 清单条目 | 现状 | 动作 |
|---|---|---|
| 分层架构（UI / 数据 / 可选 Domain） | core:model · domain · data · ui · navigation · playback + feature 11 | 已合规 |
| UI 层 = Compose + ViewModel，单向数据流 | ADR-0003 MVI 契约 | 已合规 |
| 每个屏幕拆 **Route / Screen** | 缺 | **已拆 19 屏**（§二） |
| 按功能模块化，而非按层拆分 | feature:* 11 个，feature 间禁互依 | 已合规 |
| 功能模块不依赖其他 feature 内部实现 | `check-module-deps.py` 守卫 | 已合规 |
| Navigation Compose + `rememberNavController()` + `NavHost` | MainActivity 装配 | 已合规 |
| 导航只传最小必要信息（ID） | `ArticleDetailRoute(articleId: Long)` 等 | 已合规 |
| 复杂数据存数据层单一真实来源 | Room + Repository | 已合规 |
| 类型安全导航（`@Serializable` 数据类路由） | `core/navigation/Routes.kt` | 已合规 |

### 2. 状态管理

| 清单条目 | 现状 | 动作 |
|---|---|---|
| 状态提升到最低共同祖先 | 已合规 | — |
| `remember` / `rememberSaveable` / ViewModel 分级 | `remember` 普遍；`rememberSaveable` 0 处，但无横竖屏丢状态问题 | 观察项 |
| ViewModel 用 `StateFlow` 暴露 | 已合规 | — |
| `stateIn` + `SharingStarted.WhileSubscribed(5_000)` | Subscriptions / Library / Search 等已用 | 已合规 |
| `collectAsStateWithLifecycle()` | **65 处 `collectAsState`，且依赖不在版本目录** | **全量迁移**（§二） |
| `sealed interface` 建模加载 / 错误 / 成功 | 11 个 VM 已有 UiState；9 个 VM 仍是碎片 StateFlow | 待做（批次 4） |

### 3. 组件复用与 API 设计

| 清单条目 | 现状 | 动作 |
|---|---|---|
| 单一职责 / 自下而上构建 | 已合规 | — |
| 受控组件（状态由调用方提升） | Screen 依赖 VM | 随 Route/Screen 拆分一并解决 |
| Slot API（`@Composable () -> Unit` 等） | `SettingsSubPage(content)`、`DefaultContent` 系列已用 | 已合规 |
| 必需参数在前、可选在后 | 已合规 | 已合规 |
| `modifier: Modifier = Modifier` 放第一个可选参数位置 | **7 个通用组件缺 modifier** | **已补齐**（§二） |
| `Modifier` 应用到内部根布局 | 同上 | 已补齐 |
| Compound Component + `CompositionLocal` | `CompositionLocalRoot`、`LocalRadarColors` 等 6 个 Local | 已合规 |
| State Holder 模式 + `remember` 创建 | `PagedSnapshot` 等 | 已合规 |
| 通用组件放 `:core:ui` | 已合规 | — |

### 4. 性能优化

| 清单条目 | 现状 | 动作 |
|---|---|---|
| 昂贵计算 `remember(key)` 缓存 | 已合规 | — |
| Lazy `items()` 提供稳定 key | 覆盖率极高（`docs/perf/compose-performance.md` 已核） | 已合规 |
| `derivedStateOf` 限制重组 | 3 处，均用于「滚到底加载更多」 | 已合规 |
| 延迟状态读取 / lambda 修饰符 | `Modifier.offset {}`、`graphicsLayer {}` 已用 | 已合规 |
| `@Immutable` / `@Stable` 标记 | 0 处 | **不采用**（见 §三） |
| Release + R8 下测性能 | `optimization { enable = true }` 已配 | 已合规 |
| Baseline Profile | 无 | **不采用**（见 §三） |
| Macrobenchmark | 无 `:benchmark` 模块 | **不采用**（见 §三） |

### 5. 副作用与预览

| 清单条目 | 现状 | 动作 |
|---|---|---|
| Composable 无副作用 | DAO/Repository 全在 VM 的 `viewModelScope` | 已合规 |
| 副作用放 `LaunchedEffect` / `DisposableEffect` | `LaunchedEffect` 普遍；`DisposableEffect` 4 处 | 已合规 |
| Screen 接收状态、输出事件，便于预览 | 缺 | 随 Route/Screen 拆分解决 |
| 为同一 Screen 建多个 `@Preview` | **0 处** | **已补 27 个**（13 个文件，§二） |
| 新项目用 Material 3 | M3 Expressive | 已合规 |
| `WindowSizeClass` 自适应布局 | 0 处 | 待做（§三） |

### 6. 测试与质量保障

| 清单条目 | 现状 | 动作 |
|---|---|---|
| `createComposeRule()` 测独立可组合项 | 0 处 | **已补**（`CoreUiComponentsTest` 5 例） |
| `createAndroidComposeRule<Activity>()` | 0 处 | 未用：`createComposeRule()` 已够（默认宿主 Activity 由 `ui-test-manifest` 提供） |
| 语义树定位（`onNodeWithText` / `onNodeWithContentDescription` 等） | 0 处 | **已补**：`onNodeWithText` + `isToggleable` |
| 不要依赖视图层级查找元素 | — | 严格用语义匹配器，无 `onChildAt` 一类层级查找 |
| 优先测 Screen 而非 Route | Screen 仍依赖 VM | **已解决**：Screen 全部纯 UI，测试直接喂 `UiState` + 空回调，不启动 Hilt |

---

## 二、本次落地

### 批次 1 · core:ui 组件 API 规范化（完成）

`SettingsComponents.kt`（SectionHeader / SettingSwitchRow / OptionRow / NavigateRow / SettingsSubPage）、
`OptionPickerSheet.kt`、`ArticleContextMenu.kt`：补 `modifier: Modifier = Modifier`，
放第一个可选参数位置并应用到内部根布局。

**连带修复**：`SectionHeader` 的 modifier 插到第二参后，全仓 `SectionHeader(title, desc)`
位置参数调用失效（`String but Modifier expected`）。已全部改为 `description = ...` 命名参数。

### 批次 2 · 生命周期感知收集（完成）

- `gradle/libs.versions.toml` 新增 `androidx-lifecycle-runtime-compose`（版本跟随 `lifecycleRuntimeKtx`）。
- `core:ui/build.gradle.kts` 以 `api` 暴露 → 各 feature 一次继承，无需逐个加依赖。
- **65 处** `collectAsState()` → `collectAsStateWithLifecycle()`，覆盖 **26 个文件**，0 残留。
- `scripts/check-kotlin.py` 的 `DEPS` 同步补 `lifecycle-runtime-compose-android`。

### 批次 3 · Route / Screen 拆分（完成，26 个 Destination）

**命名决策**：`core.navigation` 的 `XxxRoute` 是**路由标识对象**（CONTEXT.md 定义），
composable 包装不能同名（import 冲突）。改用项目词汇表的 **`XxxDestination`**（CONTEXT.md「目的地」）。

| 模块 | 已拆 Destination | 说明 |
|---|---|---|
| settings | SettingsGeneral / SettingsSync / SettingsRssHub / SettingsAiDiag / RssHubSettings / FilterRules / NotificationSettings / UpdateCheckRow | 8 |
| me | Backup / ReadingStats / CrashLog / FetchDiagnostics | 4 |
| library | Library | 1 |
| annotations | Annotations | 1 |
| ai | AiArtifacts / AiFeatures / InterestProfile / PromptTemplates | 4；子组件 `AiBudgetSection`、`AiQueueSection` 的 `viewModel` 参数一并换成 `onIntent` |
| player | AudioPlayer | 1 |
| feed | FeedList / FeedArticles | 2 |
| subscriptions | Subscriptions / FeedAction | 2；Screen 收 `SubscriptionsUiState` 快照，两个弹层经 **Slot 参数注入**（`groupActionSheet` / `feedActionSheet`），Screen 全程不持有 VM |
| search | Search | 1 |
| article | ArticleDetail | 1；17 条状态流收进 `ArticleDetailUiState`，命令收进 `ArticleDetailActions`，Body 也不碰 VM |

- 每个 Screen 补 2–3 个 `@Preview`（浅色 / 深色 / 异常态）。
- 11 个 feature 模块加 `debugImplementation(libs.androidx.compose.ui.tooling)`。
- `MainActivity` 的 NavHost 调用点全部改为 `XxxDestination(...)`，VM 获取收进 Destination。

**验证**：`check-kotlin.py --main-only` 由 19 error 收敛到 **0 error**；`run-tests.py` **678 tests OK**。
迭代中每改一个模块跑一次；工具链能抓 unresolved reference / 类型不匹配 / `@Composable` 丢失，
抓不到资源合并与 Hilt 聚合。

---

## 三、明确不采用（附理由）

| 清单条目 | 不采用的理由 |
|---|---|
| `@Immutable` / `@Stable` 注解 | Kotlin 2.2.10 起 strong skipping 默认开启，编译器已能推断；官方把该注解排在最后一位（覆写推断可能漏掉本该发生的重组）。详见 `docs/perf/compose-performance.md` 附录 A |
| Baseline Profile | 生成必须跑 Gradle 与插桩设备，与「开发机禁跑 gradle」约束冲突。CONTEXT.md 已记为**未采用** |
| Macrobenchmark | 同上；且 release 真机实测已用 adb `gfxinfo` 完成（`docs/performance-measurement.md`） |
| `contentType` / `unreadIds` / `pressScale` 优化 | release 实测判「无收益」，见 `compose-performance.md` §8.3 |
| `WindowSizeClass` | 目前是手机单形态产品，自适应布局需先有平板设计稿，暂缓 |

---

## 四、剩余工作

### 批次 3 收尾说明

`AddSubscriptionSheet` 未拆，且**不应拆**：它的 VM 必须活在抽屉之外（系统分享进来的链接要在抽屉
打开前就预填地址），因此 `MainActivity` 持有 `addVm` 并作为 Route 层传入 —— 这正是清单要求的
「Route 连接 ViewModel、Screen 只收状态」形态，只是 Route 落在 Activity 上。

### 批次 4 · 统一 `sealed interface UiState`（完成）

**VM 层已暴露单一 `uiState` / `state`**：FeedList / Search / Backup / FilterRules / CrashLog /
ReadingStats / Library / RssHubSettings / Update / PromptTemplates / AiArtifacts / AiFeatures /
InterestProfile / AddSubscription / Notification（单流，天然只有一个）。

**本轮新聚合（碎片流 → 单一 UiState）**：

| VM | 聚合前 | 聚合后 |
|---|---|---|
| `ArticleDetailViewModel` | 17 条 StateFlow，UI 侧 17 个订阅点 | `combine` 分 4 组（5+5+3+4）合成 `ArticleDetailUiState` |
| `SubscriptionsViewModel` | 11 条流（含 Compose `mutableStateOf` 的 `uiMessage`） | `uiMessage` 改 `StateFlow` 后分 3 组合成 `SubscriptionsUiState` |
| `AudioPlayerViewModel` | `playback.state` + `missing` + `queue` | 3 组合成 `AudioPlayerUiState` |
| `AnnotationsViewModel` | `items` + `pendingDelete` | 2 组合成 `AnnotationsUiState` |
| `FeedArticlesViewModel` | 6 个 Compose `mutableStateOf` | `snapshotFlow` 桥接成 `FeedArticlesUiState` |
| `FetchDiagnosticsViewModel` | `problems` + `hostStats` | 2 组合成 `FetchDiagnosticsUiState` |

**订阅点收敛**：`ArticleDetailScreen` / `SubscriptionsScreen` 各只剩 1 个 `collectAsStateWithLifecycle()`；
全仓该调用从迁移初期的 65 处降到 36 处。

**分组 combine 的原因**：Kotlin Flow 的 `combine` 最多接 5 个流。分组只影响构造，不改对外语义。

**`snapshotFlow` 的用法**：`FeedArticlesViewModel` 的状态是 Compose `mutableStateOf`（快照感知），
用 `snapshotFlow { ... }` 桥接成 StateFlow —— 既保留「赋值即触发」的写法，又满足「UI 从 StateFlow
收集状态」的契约。其余 VM 不存在这种混用。

**UiState 定义位置**：新的 UiState 一律定义在 VM 侧（状态是 VM 的契约，Screen 只是消费者）；
`ArticleDetailActions` 这类「命令出口」留在 Screen 侧，由 Destination 构造。

**命名不统一（未动）**：项目里既有 `uiState` 也有 `state`（Backup / FilterRules / CrashLog 用 `state`，
FeedList / Search 用 `uiState`）。两者语义相同，改名会牵动大量调用点、收益仅观感，故保持现状。

### 批次 6 · Compose UI 测试（完成）

**落地位置**：`app/src/androidTest/java/com/cycling/rssradar/ui/`（app 依赖全部 feature，一处可测所有 Screen）。

| 文件 | 覆盖 |
|---|---|
| `CoreUiComponentsTest` | 5 例：`SettingSwitchRow` 标签/副标题渲染、开关点击回调、**禁用态仍展示解释文案**（UI 铁律）、`EmptyState` 主次文案、`OptionRow` 点击回调 |
| `ScreenBehaviorTest` | 3 例：`FeedListScreen` 首屏在途不渲染空态 / 首屏落地后渲染空态 / 深色主题同样渲染 |

**关键点**：
- 组件级测试只用组件自己的 `String` 参数（不碰资源），断言不受 i18n 影响；Screen 级用
  `InstrumentationRegistry.targetContext.getString(R.string…)` 取文案。
- `FeedListScreen` 的 `isFirstLoad` 断言正好覆盖一个真实缺陷场景：空库查询期间先闪一屏
  「还没有订阅」再被真实列表覆盖。

**本地可验证性（本次一并解决）**：
`check-kotlin.py` 原先只编 main + 部分 test，androidTest 完全在盲区。已扩展：
- `src_roots` 的 test 块加入 `app/src/androidTest/java`
- `DEPS` 加入 `ui-test-junit4-android` / `ui-test-android` / `androidx.test.*` / `espresso-core`（均在 gradle 缓存里）

现在 `check-kotlin.py` 覆盖 **395 个文件 / 98 项依赖**，UI 测试的类型错误本地就能抓；**运行**仍需设备，
由新增的 CI job `instrumented-tests`（PR→main 触发，emulator API 34 + KVM）兜底。

**未采用 Robolectric**（能让 UI 测试在 JVM 跑）：它的 android-all jar 不在本地缓存，引入后本地依然跑不起来，
只会多一层依赖；等真需要"本地跑 UI 断言"时再评估。
