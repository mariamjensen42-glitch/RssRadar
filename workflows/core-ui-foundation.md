# Workflow: core:ui —— UI 基石模块（RssRadar）

状态：已定稿（grilling 2026-09-04，两轮拷问收敛）
范围：仅 RssRadar 内部模块化，不背跨项目复用包袱。

## 决策记录（拷问结论）

| 决策点 | 结论 |
|---|---|
| 定位 | 仅 RssRadar；但主题/组件仍参数化下沉，core:ui **禁止依赖 core:data** |
| palette 形态 | 重构为 CompositionLocal（RadarColors + LocalRadarColors），弃全局可变单例 |
| 确认对话框 | 新建 ConfirmDialog 组件；5 处手写 AlertDialog 中已接 GroupActionSheet 1 处，其余 4 处留给后续逐屏替换 |
| 图片封装 | RadarImage 封装；仅 FeedListScreen 走 RadarImage。**复杂场景豁免**（直接用 coil）：ArticleNativeReader（自定义解码尺寸）、ReaderImagePage（要 painter 加载态）、FeedIcon（字母块打底，RadarImage 的 surface1 底会盖掉字母）。coil import 允许存在于 core:ui 与上述 3 处豁免点 |
| utils | OpenUrl 依赖 core:data 的 LinkStore/LinkOpenMode/LinkShareState，迁 core:ui 会破坏铁律 → **留在 app**；core/ui/util 待有真正纯工具再建 |
| 骨架屏 | 本期不做 |
| 边界修订（2026-09-04 补记） | OptionPickerSheet / BottomTabBar / EmptyState 实际迁入了 core:ui（均已充分参数化、无业务依赖），追加 EmptyState 组件；依赖清单相应多了 saveable 与 lucide 两条 api（组件在用） |

## 1. 模块骨架

- `:core:ui`，目录 `core/ui/`，namespace = `com.cycling.rssradar.core.ui`。
- `core/ui/build.gradle.kts` 复制 core/data 骨架，差异：
  - plugins：`android.library` + `kotlin.compose`（库模块要出 @Composable，必须挂 compose 插件）+ `ksp` 不需要（无 Room/Hilt codegen）。
  - `buildFeatures { compose = true }`。
  - 无 packaging excludes、无 consumer-rules（暂不需要）。
- dependencies：
  - `api(platform(libs.androidx.compose.bom))` + `api` ui / ui-graphics / foundation / material3 / ui-tooling-preview
  - `api(libs.coil.compose)`、`api(libs.coil.network.okhttp)`（RadarImage 在库内，网络实现随之）
  - `api(libs.compose.icons.lucide)`、`api(libs.androidx.compose.runtime.saveable)`（BottomTabBar/OptionPickerSheet/EmptyState 在用）
  - 无 project 依赖、无 hilt、无 room。**铁律：core:ui 的源码 import 里不得出现 com.cycling.rssradar.core.data / .di / .sync**。
- `.gitignore` 已有 `**/build/`，无需处理。
- CI（ci.yml）暂不加新 task：本期 core:ui 无单测；若最终带了纯 JVM 测试再加 `:core:ui:testDebugUnitTest`。
- app：`implementation(project(":core:ui"))`。

## 2. 主题迁移（ui/theme → core/ui/theme，包名 core.ui.theme）

迁移：Color.kt、Type.kt、Theme.kt。留在 app：CompositionLocalRoot.kt（ReadingPrefs/LocalListDisplay 装配，依赖 core:data，必须留在 app）。

### palette 重构（本期唯一的重构点）

现状：`object RssRadarPalette { var bgRoot by mutableStateOf... }` + 顶层 getter（`val TextPrimary get() = ...`）+ `applyPalette(dark)`。

目标形态：

```kotlin
// core/ui/theme/Palette.kt
@Immutable
data class RadarColors(
    val bgRoot: Color, val surface1: Color, val surface2: Color, val surface3: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val divider: Color, val onAccent: Color,
) { companion object {
    val Dark: RadarColors
    val Light: RadarColors
} }

val LocalRadarColors = staticCompositionLocalOf { RadarColors.Dark }

/** 统一读取入口：radarColors().textPrimary */
@Composable
fun radarColors(): RadarColors = LocalRadarColors.current
```

- `RssRadarTheme(darkTheme, content)`：签名不变；内部改为 `CompositionLocalProvider(LocalRadarColors provides if (darkTheme) RadarColors.Dark else RadarColors.Light)` + MaterialTheme(深/浅 scheme)。
- 删除：RssRadarPalette 单例、8 个顶层 getter、applyPalette、Accent/AccentPressed 顶层常量改并入 RadarColors（accent 不随主题变，作为 RadarColors 的 accent 字段）。Danger 不进 palette（维持现状）。
- 常量（DarkBgRoot 等十六进制值）保留为 private 或 RadarColors.Companion 内部，不再对外。

### 调用点清扫（机械替换，全 ui/ 约 40 文件）

- `TextPrimary` → `radarColors().textPrimary`（同型替换 textSecondary/textTertiary/bgRoot/surface1/2/3/divider/accent/Accent/AccentPressed→.accent/.accentPressed、OnAccent→.onAccent）。
- 非组合上下文（如有）读色：无。执行时 grep 确认 `ui/` 下无残留 `RssRadarPalette|applyPalette|BgRoot|Surface1|Surface2|Surface3|TextPrimary|TextSecondary|TextTertiary|Divider|Accent\b|OnAccent|Link\b`（Link 同理并入 RadarColors.link）。
- 深浅主题判断继续用 app 里 LocalDarkTheme（CompositionLocalRoot），不动。

## 3. 通用组件（ui/components → core/ui/components，包名 core.ui.components）

迁移并解耦：

- **AppSnackbar.kt** → 原样迁，色引用改 radarColors()。API 不变。
- **FeedIcon.kt** → 参数去业务化：改为接收 `(url: String?, fallbackName: String, …尺寸/形状参数)`，不接收 Feed/RouteCatalog 类型。app 调用点传 url 与名称。
- **OpenUrl.kt** → 保留在 app（耦合 core:data 的 LinkStore，见决策记录）；core/ui/util 待有真正纯工具再建。

新建：

- **ConfirmDialog.kt**（core/ui/components）：

```kotlin
@Composable
fun ConfirmDialog(
    title: String,
    text: String? = null,
    confirmText: String,
    dismissText: String? = null,
    destructive: Boolean = false,   // true 时确认按钮用 danger 色
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```
  本期**不替换**现有 5 处手写 AlertDialog（CrashLogScreen / FeedActionScreen / GroupActionSheet / SubscriptionsScreen / ReaderImagePage），留给后续逐屏替换。
- **RadarImage.kt**（core/ui/components）：包 coil AsyncImage：

```kotlin
@Composable
fun RadarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
)
```
  行为：crossfade(true)；url 空/加载中 → Surface1 底色占位；失败 → Surface1 底 + lucide 风线性兜底图标（描边风格，禁 Filled）。ImageRequest 遵守内存+磁盘缓存默认。
- **骨架屏：不做**。

### 图片调用点（2026-09-04 修订）

FeedListScreen 走 RadarImage；3 处复杂场景豁免直用 coil：`article/ArticleNativeReader.kt`（自定义解码尺寸防 OOM）、`article/ReaderImagePage.kt`（painter 加载态驱动转圈）、`components/FeedIcon.kt`（库内；字母块打底，不吃 surface1 底色）。

## 4. 交付边界（2026-09-04 修订）

- ~~不动 OptionPickerSheet / ArticleContextMenu / BottomTabBar~~ → 修订：OptionPickerSheet / BottomTabBar 已迁入 core:ui（参数化充分，见决策记录）；ArticleContextMenu 仍留 app。
- 新增 EmptyState 组件（spec 外追加，已记决策）。
- 不做骨架屏、不做 Modifier/String 大盘点（后续现搬现收进 core/ui/util）。
- 不引入新第三方库（lucide 图标包沿用 compose-icons）。

## 5. 验证（无 gradle 环境）

1. `python scripts/check-kotlin.py --files` 五模块全源码（app main+test、core/model、core/domain、core/data、core/ui main）→ 0 error。
2. grep 断言：core/ui 源码内无 `core.data|core.domain 以外的 project 引用`；app ui/ 无残留旧 palette 符号；coil import 不出现在 app。
3. 行为不变由 CI 单测 + 用户真机冒烟兜底（主题切换、图片加载、snackbar）。

## 6. 惯例提醒（实施 agent 必读）

- 禁止 gradle 编译；git 命令需 dangerouslyDisableSandbox；commit 到 dev；反引号测试名禁 `:` `.`。
- 图标一律线性描边风格（lucide），禁 Material Filled。
- UI 铁律：disabled 按钮必须配解释文案。
