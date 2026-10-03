# core/ui 自定义组件 → M3 Expressive 迁移

> 范围：`core/ui/src/main/kotlin/com/cycling/rssradar/core/ui/components/`
> 准则：**多使用自带的组件。M3 EXPRESSIVE。**（已写入 `AGENTS.md` + `docs/ui-resources.md` 四）
> 状态：**已执行**（静态检查 0 error）

---

## 0. 事实基线（已核实，非记忆）

| 项 | 值 | 核实方式 |
|---|---|---|
| material3 版本 | `1.5.0-alpha28` | `gradle/libs.versions.toml` |
| Expressive 组件可用性 | ButtonGroup / FloatingToolbar / LoadingIndicator / WavyProgress / MaterialShapes **全部存在** | 解压 `build/kotlinc/cp/material3-android-1.5.0-alpha28-classes.jar` 提取类名 |
| 1.4.0 是否有 Expressive Composable | **否**，仅设计 token | 记忆记录（2026-10-01 实测 aar 条目） |
| 现有已用 Expressive | `ReaderSelectionBar` 用 `HorizontalFloatingToolbar`、`AudioPlayerScreen` 用 `LoadingIndicator` | 全仓 grep |

**关键约束**：`core/ui` 的铁律是不依赖 `core:data` / `core:domain` / `di`，组件全部参数化。任何替代方案不能破坏这条。

### 踩到的两个签名坑（字节码核实，非猜）

| 组件 | 错误写法 | 正确参数名 |
|---|---|---|
| `FloatingToolbarDefaults.standardFloatingToolbarColors` | `containerColor = …` | 无参调用后 `.copy(toolbarContainerColor = …)` |
| `ContainedLoadingIndicator` | `color = …` | `containerColor = …` + `indicatorColor = …` |

`FloatingToolbarColors` 的字段名是 `toolbarContainerColor` / `toolbarContentColor` / `fabContainerColor` / `fabContentColor`。
这两个坑都是 `check-kotlin.py` 静态检查抓出来的——**不编译就靠它兜底**。

---

## 1. 已执行的改造

### 1.1 BottomTabBar.kt — 162 行 → 100 行 ✅

**最终形态：官方 `NavigationBar` 通栏**（用户 2026-10-03 明确「导航栏不需要胶囊的样子」）。

路径：手写悬浮胶囊（`Surface` + `Row` + 三个 `animateColorAsState` + 手写 `clip/background/clickable` + `Spacer` 挤标签）
→ 先试 `HorizontalFloatingToolbar`（保胶囊形态）→ **按用户要求改为 `NavigationBar` 通栏**。

- 删掉死代码 `secondaryFg`（原先用 `@Suppress("UNUSED_EXPRESSION")` 硬留）
- 修正注释与实现不符（注释写「4 个主屏条目」，实际 3 项）
- **常量更名**：`FloatingTabBarClearance` → `TabBarClearance`（64dp → **80dp**，官方 `NavigationBar` 标准高度）、`FloatingTabBarFabOffset` → `TabBarFabOffset`（88dp → 104dp）。两者经 grep 确认**全仓无引用**，改名零影响。
- 函数名 `FloatingBottomBar` **保留不改**：已不是 floating 形态，但改名要动 MainActivity 调用点，收益不抵风险。已在 KDoc 写明「名不副实」的原因。
- 外部 API 零变化：6 处 `tabBarBottomClearance()` 调用点（`FeedListScreen` ×3、`SearchScreen`、`SubscriptionsScreen`、`RssHubSettingsScreen`）**一行未改**——让位收敛在一个函数里，常量从 64 改到 80 自动生效。这正是把让位收口的回报。
- 同步更新 3 处过时注释（「悬浮 TabBar」「胶囊压住」→「底部导航栏」）。

### 1.2 Shimmer.kt — 移除第三方依赖 ✅

`compose-shimmer` 封装 → 官方 `ContainedLoadingIndicator`。
函数名 `ShimmerOverlay(modifier)` 保持不变，`RadarImage.kt` 与 `ArticleNativeReader.kt` 两个调用点**无需改动**。

连带清理（准则第 2 条「已有官方替代就移除第三方库」）：
- `core/ui/build.gradle.kts` 删 `api(libs.compose.shimmer)`
- `gradle/libs.versions.toml` 删 `composeShimmer` 版本项与 `compose-shimmer` library 项
- `scripts/check-kotlin.py` 删对应的 DEPS 行（否则仍会去缓存里翻已废弃的 jar）

### 1.3 OptionPickerSheet.kt — 手写选项行 → 官方组件 ✅

手写 `Row` + `clickable` + 自绘 `Lucide.Check` 勾选标记 → 官方 `ListItem`（`headlineContent` / `supportingContent` / `trailingContent`）+ `RadioButton`。

副标题能力保留（这是当初没用 `SingleChoiceSegmentedButton` 的原因——它只容得下单行）。
`RadioButton(onClick = null)` 只作状态指示，可点性由整行 `clickable` 承担（`ListItem` 自身不可点）。

---

## 2. 保留的组件（动了是负收益）

| 组件 | 保留理由 |
|---|---|
| `ConfirmDialog` (52) | 内部已是官方 `AlertDialog`，是无障碍/危险色语义的薄封装 |
| `AppSnackbar` (71) | 已是官方 `SnackbarHost`，内部换掉默认反色胶囊是刻意品牌化 |
| `PressScale` (40) | M3 无等价物，且承载 `LocalReducedMotion` 降级 |
| `EmptyState` (61) | M3 无空态组件 |
| `RadarImage` (87) / `FeedIcon` (124) | Coil 封装与头像合成，与 Expressive 无关 |

这三类例外已写进 `docs/ui-resources.md` 准则第 3 条。

---

## 3. 待你验证（编译期查不出）

**底部占位**：`TabBarClearance = 80.dp` 现在对应官方 `NavigationBar` 的标准高度（原先手写胶囊只有 52dp）。
导航栏仍是 overlay 不占布局，6 处调用点靠 `tabBarBottomClearance()` 预留——常量已同步为 80dp，
但**建议真机逐个 tab 屏确认**最后一条内容不被压住，尤其 FeedListScreen 的 3 处（列表 contentPadding / 空态 / 推荐流 loading）。
若日后升级 material3 改了 `NavigationBar` 默认高度，**必须回来核对这个常量**，编译期不会报。

**视觉走查**：底部从悬浮胶囊变成通栏，选中态 indicator、Shimmer 加载动效（扫光 → 形状变形）、OptionPickerSheet 选项行布局。

