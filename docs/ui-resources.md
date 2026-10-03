# RssRadar UI 资源白名单

> 基于 Awesome Android UI 方法论整理：按「用途」归类，每类只保留已锁定的主流选择。
> 选型维度：Compose 原生兼容性、库活跃度、依赖体积。新增 UI 依赖前先查本表。
> 最后更新：2026-08-30

## 一、组件白名单（按用途）

| 用途 | 选型 | 版本 | 来源 / 备注 |
|---|---|---|---|
| UI 框架 / 主题 | Jetpack Compose BOM + Material 3 | 2026.02.01 | Compose 原生，零 View 体系；**配色走系统动态取色**（Android 12+ 取壁纸色），见下 |
| 图标 | lucide（`com.composables:icons-lucide-cmp`） | 2.2.1 | 线性描边，1665 图标；可用符号真值表见 `prototype/check-symbols.py` |
| 远程图片 | Coil 3 + `coil-network-okhttp` | 3.3.0 | v3 **必须**显式加 OkHttp 引擎，否则网络图空白 |
| 文章正文(HTML) | Android `WebView`（`android.webkit.WebView`） | 系统 | `ArticleDetailScreen` 用 `AndroidView` 包 WebView 渲染净化 HTML |
| 导航 | navigation-compose（类型安全路由） | 2.10.0 | `bottomSheet` DSL 已删除，改用 `composable` + `ModalBottomSheet` |
| 弹窗 / 底部抽屉 | Material 3 `ModalBottomSheet` / `AlertDialog` | 自带 | 零新依赖 |
| 底部导航 | `NavigationBar` / `NavigationBarItem` | 自带 | 见「四、组件准则」第 1 条。**不做胶囊形态**（2026-10-03 定） |
| 下拉刷新 | 手写 `PullToRefresh` | — | 无第三方库 |
| 分页 | 手写 OFFSET 分页（PAGE_SIZE=30） | — | 无 Paging 库 |
| 状态 / DI | ViewModel + Hilt + runtime-saveable | — | — |

## 二、选型结论（保持，勿动）

- **Compose 原生优先**，拒绝 View 体系混用。
- **图标集已锁 lucide**。⚠️ 与全局 feather 偏好冲突：本项目已落地 lucide，新图标只在 lucide 取；
  切 feather 是一次性全量替换，不是增量。两套禁止混用。
- ⚠️ **本条已于 2026-10-03 修订**：原「弹窗 / 抽屉 / 刷新 / 分页全部手写或系统组件，不引第三方库」已作废。
  手写优先是为了避免小体量第三方库，**但它被扩大化成了「自己重写官方已有控件」**——
  手写底部导航（`BottomTabBar` 162 行）、手写骨架屏即由此而来。
  现准则见下方「四、组件准则」。第三方 UI 库仍不建议引入（与准则无关，是依赖预算问题）。
- **配色（2026-10-03 改为自动取色）**：主配色跟随系统动态色板（Android 12+ 取壁纸色），
  **默认开启**。`Color.kt` 里的固定色板已降级为「Android 11 及以下的回退值」，不是设计主色。
  实现见 `Theme.kt` 的 `withSystemScheme`——把 `RadarColors` 的表面/文字/分割线整体映射到
  `dynamicDarkColorScheme` / `dynamicLightColorScheme` 的 surface 族与 onSurface 族。
  **两处刻意保留**（都不是「自定义配色」而是功能）：
  1. **阅读主题**（米黄纸 / 灰 / 夜）仍用固定三套色——阅读场景下"纸的颜色"是刚需，跟随壁纸反而不利于阅读；
  2. **`MotionTokens` 动效 token** 保留——它承载 reduce-motion 无障碍降级，M3 无等价物。

## 三、已知缺口（按需再定，勿过早引入）

1. 文章内图片画廊 / 缩放：`WebView` 内可点，原生 `HorizontalPager` + 缩放缺。
2. ~~骨架屏 / shimmer~~ **已解决**：M3 `LoadingIndicator` / `ContainedLoadingIndicator` 官方自带，
   原第三方 `compose-shimmer` 封装已按「四、组件准则」第 2 条移除。
3. 富文本离线正文：`WebView` 已够，勿提前换 compose-richtext。
4. 系统分享：现只 `context.openUrl`，未接 `ACTION_SEND`。

## 四、组件准则（2026-10-03 起，最高优先级）

> **多使用自带的组件。M3 EXPRESSIVE。**

这是项目 UI 层的首要准则，优先于本文档其他条目，也优先于「保持现有视觉一致」的默认偏好。

### 1. 优先用 M3 Expressive 官方组件

material3 锁 `1.5.0-alpha28`（Expressive 组件的唯一来源；1.4.0 只有设计 token，没有可调用的 Composable）。
**写自定义控件前，先确认官方没有对应组件。** 已确认可用：

| 需求 | 用这个 | 别再手写 |
|---|---|---|
| 底部导航 | `NavigationBar` + `NavigationBarItem` | `Surface` + `Row` + `animateColorAsState` 手搓 TabBar |
| 浮动工具条 | `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | 自绘 Surface + 阴影 |
| 加载中 | `LoadingIndicator` / `ContainedLoadingIndicator` | 手写扫光 / `compose-shimmer` |
| 按钮组 | `ButtonGroup` | 手动 `Row` + 间距拼按钮组 |
| 进度 | Wavy progress（`CircularWavyProgress` 等） | 自绘波浪进度 |
| 形状系统 | `MaterialShapes` | 手写 `RoundedCornerShape` 拼形状语言 |
| 弹窗 / 抽屉 / 菜单 | `AlertDialog` / `ModalBottomSheet` / `DropdownMenu` | 手写遮罩 + 弹层 |
| 单选组 | `SingleChoiceSegmentedButtonRow` / `ListItem` + `RadioButton` | 手写选项行 + 自绘勾选标记 |
| 吐司 | `SnackbarHost` | 手写遮罩 + 定时消失 |
| 底部导航占位 | `WindowInsets.navigationBars` 派生 | 硬编码 Dp 常量 |

### 2. 不为「统一外观」引入第三方 UI 库

官方组件够用时一律用官方。确需第三方库（如 `compose-shimmer` 曾用于骨架屏）时，
先确认官方无等价物；**已有官方替代的，移除第三方依赖**。

### 3. 手写只在官方确实没有时

允许手写的三类，且都已有既存理由：

- **无障碍降级逻辑**（`PressScale`：M3 无等价物，且要接 `LocalReducedMotion`）
- **品牌化外观**（`AppSnackbar` 内部换掉 M3 默认反色胶囊；`EmptyState` M3 无空态组件）
- **平台 IO 封装**（`OpenUrl`：Intent 跳转，无 UI）

### 4. 改组件后必须实测运行时视觉

底部导航栏是 overlay 不占布局，各 tab 屏靠 `tabBarBottomClearance()` 预留让位。
该值对应官方 `NavigationBar` 的标准高度（80dp），**组件自己定高、我们只是跟随**：
升级 material3 若默认高度变了，必须回来核对 `TabBarClearance`——**编译期查不出**，
症状是列表最后一条内容被导航栏压住。涉及 `FeedListScreen`、`SearchScreen`、`SubscriptionsScreen`、`RssHubSettingsScreen`。

## 五、避坑清单（本项目已踩）

- Coil 3 必须加 `coil-network-okhttp`，否则网络图全空白（封面空白根因）。
- `SubcomposeAsyncImage` 默认加载态不绘制，必须显式 `loading`/`error` 占位，否则透明空洞。
- nav 2.10 删了 `bottomSheet` DSL → 用 `composable` + `ModalBottomSheet` 替代，行为等价（进栈、预测性返回）。
- 跨文件私有扩展不可访问（如 `withoutScheme`）→ 统一用标准库 `removePrefix`。
- 通用三忌：别混图标集、别为酷炫动效引大库拖慢包体、别直接搬 demo 上生产不改边界。
- 通用四忌（2026-10-03 增补）：**别手搓官方已有控件**——这是本项目最贵的一条，
  已付出代价：`BottomTabBar` 手写 162 行（选中动画、圆角、inset 全自己维护），
  而 `ReaderSelectionBar` 早就在用官方 `HorizontalFloatingToolbar` 了。
