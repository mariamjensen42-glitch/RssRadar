# 采用 androidx.navigation 做类型安全导航，并用 Hilt 接管依赖注入

RssRadar 现状用手写状态路由（`MainActivity` 中 3 个 `rememberSaveable` + `when(MainTab)` 切换 4 屏），DI 用手写 `AppContainer`；随着屏与 Sheet 增多，Back/DeepLink/状态保存需自管，且 6 个 ViewModel 全挤在 `MainActivity` 构造、耦合重。决定引入 `androidx.navigation:navigation-compose` 做类型安全路由，并同步引入 **Hilt 标准全量**接管 DI（删除 `AppContainer`）。两者一起重构：加订阅两步流需要 navGraph 作用域的共享 ViewModel，而 Hilt 的 `hiltViewModel()` 正是其干净实现；分两轮做反而要把 ViewModel 装配改两遍。

## Status

accepted

## Considered Options

**导航库**

- 手写状态路由（现状）：可控，但 Back/DeepLink/状态保存全要自管，路由逻辑散落 `MainActivity`，难以测试。否决。
- `androidx.navigation:navigation-compose`（选）：官方维护、与 Compose 集成最好、类型安全路由消除字符串拼接错、Back/DeepLink/PredictiveBack/navTesting 现成，当前依赖零冲突。
- 第三方 Voyager / Appyx / Compose Destinations：能力强但引入非官方依赖与额外心智，收益不及官方方案。否决。

**依赖注入**

- 手写 `AppContainer`（现状）：轻量，但 6 个 VM 装配耦合在 `MainActivity`，且无法用 `hiltViewModel()` 做导航作用域。否决。
- Hilt 标准全量（选）：`@HiltAndroidApp` / `@AndroidEntryPoint` / `@HiltViewModel` + `@Module` 提供 Room DB 与解析器；与 `navigation-compose` 的 `hiltViewModel()` 无缝配合。
- Hilt 半吊子（仅注入 `AppContainer`）：留「手写 + Hilt」双 DI 并存。否决。
- Koin：非官方，与 `navigation-compose` 官方的 `hiltViewModel()` 集成不如 Hilt 直接。否决。

**路由写法**：字符串 + `navArgument`（易拼错，否决）vs `@Serializable` 类型安全路由（选，编译期校验）。

**底部导航**：`MainTab` 枚举 + 映射层（双真相源，否决）vs 删 `MainTab` 枚举、底栏吃当前 route（选）。

**状态保存**：`rememberSaveable` 手管 3 状态（否决）vs Nav back stack + `savedStateHandle`（选）。

**导航作用域 ViewModel**：全部目的地作用域（过度，否决）vs 仅加订阅两步流 navGraph 作用域 + 其余 `hiltViewModel()`（选）vs Activity 聚合 VM（回到现状耦合，否决）。

## Consequences

- `MainActivity` 的 3 状态 + `when(MainTab)` 改写为 NavGraph：4 主屏（`Feed` / `Subscriptions` / `Search` / `Me`）为顶层 composable 目的地；`ArticleDetail(id)` 为独立 composable 目的地；`AddSubscription` / `FeedAction(id)` 为 `bottomSheet` 目的地；`Group` 对话框保留 `AlertDialog`。
- 删 `MainTab` 枚举；`BottomTabBar` 改为接收 `(currentRoute, onTabSelected)`，选中态由 `NavController` 当前 route 决定。
- 删手写 `AppContainer` 及其全部 `viewModelFactory { initializer { ... } }` / `by viewModels { factory(container) }`；ViewModel 改 `@HiltViewModel @Inject constructor(...)`，依赖由 Hilt `@Module` 提供（Room DB、`Rome`/`Jsoup`/`readability4j` 解析器）。
- 启用 PredictiveBack（Nav 内置）；为 `ArticleDetail(id)` 定义 deepLink `rssradar://article/{id}`，manifest `intent-filter` 在迁移 PR 注册。
- 迁移 PR 需新增依赖：`androidx.navigation:navigation-compose`（与 Compose BOM 2026.02.01 兼容版本，于 `libs.versions.toml` 固定）、`hilt-android` + `hilt-compiler`（KSP 处理；Hilt 2.5x+ 兼容 Kotlin 2.2.10 / KSP 2.2.10-2.0.2）。
- 范围扩大：本次同时动导航与 DI（用户显式推翻 Q3「仅导航」的范围），改动面大于单纯导航。实现遵循「一 issue 一 PR」，建议拆「导航」与「Hilt DI」两个 PR，均引用本 ADR。
- 不在本期：导航测试（Q12=B）。后续迁移 PR 补 navTesting 冒烟（验证 Feed → `ArticleDetail(id)` 带参正确），以锁住类型安全路由不被改坏。
