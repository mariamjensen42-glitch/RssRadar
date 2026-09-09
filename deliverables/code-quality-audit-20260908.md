# RssRadar 代码质量与可维护性审查

- 日期：2026-09-08
- 分支：dev @ `19a09a5`
- 范围：`app/src/main/java`（55 个 kt / 19766 行）+ `core/ui`（复用层）+ `app/src/main/res/values`
- 方法：词频统计（`grep -rnoE` + `uniq -c`）+ 关键文件人工解剖。所有数字可用文末命令复核。

---

## 结论速览

| 维度 | 评级 | 一句话 |
|---|---|---|
| 硬编码 | **D** | 文案资源化率 **0%**；间距 1007 处裸写；颜色字号反而管得很好 |
| Design Token | **B-** | 6 类 token 里 2 类完全集中（色/字）、**4 类根本不存在**（间距/圆角/高度/透明度） |
| 组件复用 | **D** | 复用层已建 9 个组件，但被系统性绕过；文章卡片 6 个变体、设置行 6 套实现 |
| 组件颗粒度 | **C-** | 主要矛盾是**过粗**（单函数 446 行）而非过细；另有更致命的跨层反向依赖 |

四个维度里三个不及格。好消息是病灶很集中，不是弥散性腐烂。

---

## 一、硬编码使用范围

### 1.1 字符串：资源化率 0.00%

| 指标 | 数值 |
|---|---|
| `Text("中文")` 字面量 | 114+（含中文字符串字面量共 834 处 / 2407 行） |
| `stringResource` / `getString()` | **0** |
| `strings.xml` 条目 | **2**（等于没有） |

这是全项目最刺眼的一项：一个纯中文 App，`res/values/strings.xml` 只有 2 条，国际化、文案统一收口、甚至"同一句话改两处"的能力全部归零。

已经在漏的实例：
- `OpenUrl.kt:36` 与 `ArticleContextMenu.kt:228` 写着同一句「无法打开链接」；「无法分享」同理重复两次
- `InterestProfileScreen.kt:164` `if (state.terms.size > 30)`，164 行又把 30 写进字符串 `"仅展示权重最高的 30 个"` —— 阈值和文案各维护一份，改一个忘一个

Top 文件：`SettingsSubPages.kt`(86) / `FeedListScreen.kt`(59) / `AiFeaturesScreen.kt`(53) / `SubscriptionsScreen.kt`(50) / `AiArticleSheet.kt`(46)

### 1.2 颜色：管得最好的一类

- app 模块裸 `Color(0x...)`：**仅 3 处**（`AddSubscriptionSheet.kt:80` `0xFFFF6B00`、`:885` `0xFF111114`、`ArticleNativeReader.kt:693` `0x66FFC107`）
- `radarColors()` 调用 **699 次**，34/38 个含 `@Composable` 的文件接入
- 不存在"同一语义色在多文件重复写 hex"

唯一值得挑的：`AddSubscriptionSheet.kt:885` 的 `Color(0xFF111114)` 是**深色模式专用值**，浅色模式不跟随——一个 if 分支写死了主题。

### 1.3 尺寸：重灾区

- `.dp` 字面量 **1007 次**，去重 **42 种**；具名常量只提取了 8 个
- Top：`8.dp`×194、`12.dp`×121、`14.dp`×98、`10.dp`×86、`6.dp`×85、`4.dp`×79、`20.dp`×71
- `.sp` 字面量 **0** —— 字号全走 `MaterialTheme.typography`，这块干净

1007 处裸尺寸意味着：想改一次全局间距基线，得做 1007 次判断。

### 1.4 魔法数字

- `alpha =` 14 处（`0.5f`×4、`0.16f`×2、`0.55f`×2）
- `ArticleWebView.kt:118-120` `postDelayed(300 / 800 / 2000)` 三个裸延时
- `ReaderImagePage.kt:170` JPEG 质量裸写 `92`
- `BodyMode.kt:107` `SUMMARY_SWITCH_MIN_GAIN = 120`、`ReadingNodes.kt:29/32` `MAX_DEPTH=24`/`MAX_BLOCKS=800` —— 这几个**抽了常量，是正面样本**
- `FeedListViewModel.kt:537` `PAGE_SIZE = 30`，但同文件注释里又写了一遍「每页 30 条」，两处独立维护

### 1.5 接口地址 / 外部依赖

app 层 URL 字面量仅 3 处，且都是 UI 占位（`AddSubscriptionSheet.kt:921` 示例地址、`RssHubSettingsScreen.kt:708` placeholder、`SettingsSubPages.kt:354` 协议补全逻辑）。

真正的端点在 `core/data`，收口良好：`DeepSeekClient.kt:98 BASE_URL`、`RouteCatalogStore.kt:109 SOURCE_URL`、`RssHubInstanceStore.kt:89-99` 镜像列表（11 个域名硬编码，但集中一处，可接受）。

`BuildConfig` / `User-Agent` / `preferencesKey` 字面量：0 处散落。

**本维度判定：外部依赖收口合格，颜色字号合格；文案与尺寸全线失守。**

---

## 二、Design Token 统一管理

### 2.1 先纠正一个容易误判的地方

`app/.../ui/theme/` 下的 `CompositionLocalRoot.kt`（109 行）**不是 token 层**，它只导出三个行为型 CompositionLocal：

```kotlin
// CompositionLocalRoot.kt:31,34,37
val LocalDarkTheme    = staticCompositionLocalOf { true }
val LocalReadingPrefs = staticCompositionLocalOf { ReadingPrefs() }
val LocalListDisplay  = staticCompositionLocalOf { ListDisplayState() }
```

真正的 token 在 `core/ui/.../theme/`：`Color.kt`(139) / `Type.kt`(82) / `Theme.kt`(71) / `Motion.kt`(92)。**缺 `Shapes.kt` 和 `Dimens.kt`。**

### 2.2 覆盖率：颜色 A 级，其余 D 级

| Token 类 | 有无定义 | 覆盖 | 评级 |
|---|---|---|---|
| 颜色 | `RadarColors` 14 语义色 + 3 状态色 | 34/38 文件 | **A** |
| 字号 | `Type.kt` 集中，UI 层 0 硬编码 | 26 文件 | **A** |
| 间距 | **无** | 1007 处裸写 | **D** |
| 圆角 | **无** | 130 处 `RoundedCornerShape` | **D** |
| 高度 | **无** | 15 种裸值 | **D** |
| 透明度 | **无** | 14 处 `alpha` | **D** |

### 2.3 重复定义取证

| 值 | 次数 | 分布 |
|---|---|---|
| `RoundedCornerShape(14.dp)` | **37** | `FeedListScreen.kt`×7/8、`AiFeaturesScreen.kt`×3、`AddSubscriptionSheet.kt`×2 … |
| `RoundedCornerShape(12.dp)` | 33 | 全项目 |
| `RoundedCornerShape(50)`（胶囊） | 30 | 全项目 |
| `Spacer(height(8.dp))` | **47** | `AddSubscriptionSheet.kt`×6、`AiArticleSheet.kt`×2 … |
| `padding(horizontal = 20.dp)` | ≥12 | `ReadingBody.kt`×6、`AiArtifactsScreen.kt`×2 … |
| `Color.Black.copy(alpha = 0.5f)` | 3 | `ReaderImagePage.kt:134/218/232` |

红色系存在**三套互不相干的定义**：`Color.kt:75 Danger = 0xFFEF4444`、`Theme.kt:33 error = 0xFFEF4444`（同值各写一遍）、`Theme.kt:52 0xFFDC2626`（第三套）。`0xFF6B7CFF` 亦然：`Color.kt:37` 是 token，`FeedIcon.kt:106` 是裸值。

### 2.4 规范写在注释里，不在代码里

```kotlin
// app/.../ui/components/ArticleContextMenu.kt:115
// iOS Dark 风：radarColors().surface1 卡片面 + 14dp 圆角 + radarColors().divider 细描边
```

这条注释就是全项目卡片样式的"规范文档"。注释不能参与重构，改起来照样 37 处手动替换。

### 2.5 混用风险：基本没有

`Theme.kt:18-54` 把 M3 `colorScheme` 槽位全部由同一批常量映射（`primary = AccentValue`、`surface = DarkSurface1`、`outline = DarkDivider`），两套同源不会漂移。业务层直接引用 `MaterialTheme.colorScheme` 的只有 2 处（`MainActivity.kt:119`、`AddSubscriptionSheet.kt:1018`）。

**本维度判定：不是"覆盖率低"，而是"token 种类覆盖不足（2/6）"。已建的两类质量很高，缺的四类是纯粹欠账。**

---

## 三、组件复用情况

### 3.1 复用层存在，但被系统性绕过

`core/ui/components/` 已有 9 个组件，实际调用：

| 组件 | 调用次数 |
|---|---|
| `EmptyState` | **1**（`AiArtifactsScreen.kt:185`） |
| `ConfirmDialog` | **0** —— `GroupActionSheet.kt:213` 定义了同名 private 版把它遮蔽了 |
| `OptionPickerSheet` | 5 |

`app/.../ui/components/` 只有 2 个文件、298 行，占总量 **1.5%**。

### 3.2 重复实例清单

| 类别 | 重复份数 | 证据 |
|---|---|---|
| 文章卡片渲染器 | **6** | `FeedListScreen.kt` 内 4 个（`MagazineHeroCard:917` / `MagazineCard:1004` / `GridArticleCard:1151` / `ArticleCard:1452`）+ `SearchScreen.kt:368 SearchResultRow` + `SubscriptionsScreen.kt:702 FeedRow` |
| 长按菜单样板 | **5** | `FeedListScreen.kt:890/1343/1493`、`SearchScreen.kt:400` —— 同文件 `:868 ArticleMenuBox` 已抽出该逻辑，自己的 `ArticleCard` 却没用 |
| 设置行 | **6 套实现** | `SettingsSubPages.kt:135 SettingSwitchRow`、`FeedActionScreen.kt:467 SwitchRow`、`AiFeaturesScreen.kt:521 内联 FeatureRow`、`SettingsSubPages.kt:161 OptionRow`、`:197 NavigateRow`、`RssHubSettingsScreen.kt:495 SettingsEntryCard` |
| 空状态 | **6** | `core.ui.EmptyState`（1 调用）+ `FeedListScreen.kt:1778` 私有重名版 + `FeedArticlesScreen:100` + `CrashLogScreen:146` + `FetchDiagnosticsScreen:118` + `SearchScreen:352` + `ReadingStatsScreen:299` |
| Chip | **6 份私有实现** | `FeedListScreen.kt:508` / `AiArtifactsScreen.kt:285` / `AddSubscriptionSheet.kt:983` 三者签名几乎相同（`Surface(50, if(selected) accent else surface2)`） |
| 顶部栏 | **11 屏手写 Row**，仅 1 处用 M3 `TopAppBar` | `Lucide.ArrowLeft` 12 处，4 种 padding 变体 |
| 输入框配色块 | **15** | `OutlinedTextFieldDefaults.colors(...)`，`unfocusedBorderColor = Color.Transparent` 出现 13 次 |
| `CircularProgressIndicator` | **37** | 散落 12 文件，尺寸/配色不统一 |
| `ChevronRight` | **15** | 散落 6 文件 |

`CrashLogScreen.kt:146-160` 与 `FetchDiagnosticsScreen.kt:118-133` 近乎逐字相同：同样的 `padding(horizontal = 20.dp, vertical = 40.dp)` + 主文案 `textSecondary/bodyMedium` + `Spacer(6.dp)` + 次文案 `textTertiary/bodySmall`。

`SettingsSubPages.kt` 内已有 `OptionRow`/`NavigateRow`，但同文件还有 **17 处内联标签行**（:280 / :316 / :355 / :409 / :509 / :555 / :656 / :780 / :795 …），`:409` 的兴趣画像行与 `NavigateRow`(:197) 结构完全一致却没复用。

**本维度判定：A（严重）。问题不是"没建复用层"，而是"建了不用 + 私有副本覆盖公共组件"。**

---

## 四、组件颗粒度

### 4.1 过粗：主要矛盾

前 5 大 UI 文件 5816 行，占总量 **29%**。

**`FeedListScreen.kt`（1872 行 / 24 个 `@Composable`）—— 单文件 9 类职责：**

| 职责 | 行号 |
|---|---|
| 页面编排 + 下拉刷新 + 分页触发 | 139–378（239 行） |
| 顶栏/溢出菜单 | 379–458 |
| Tab 行 | 459–506 |
| 筛选 chip + 分组 BottomSheet | 507–629 |
| 4 种视图渲染 | 917–1237 |
| 粘性日期头 + 自绘滚动条 | 1238–1300 |
| 卡片 + 滑动手势 + 长按菜单 | 867–916, 1301–1611 |
| 图片画廊 | 1682–1761 |
| 空态/加载态 | 1777–1873 |

**`SubscriptionsScreen.kt`：单函数 92–538 共 446 行**，内含 SAF 导入导出 launcher、搜索框、失效源 chip、分组树 stickyHeader、**6 个 AlertDialog**（396–480）、排序弹层。`FeedRow` 调用块还在 309–323 与 366–386 逐字重复。

其他：`ArticleDetailScreen.kt:436-714 ReadingStyleSheet` 单函数 278 行；`AddSubscriptionSheet.kt` 一个文件塞下两步向导 7 类职责；`SettingsSubPages.kt` 4 个互不相干的页面共存（266–488 / 488–642 / 642–841 / 841–967）。

### 4.2 过细：局部噪音，非主要矛盾

- **死代码**：`FeedListScreen.kt:1868 LoadingPlaceholder` 标注 `@Suppress("unused")`，全仓 0 调用
- 单调用点且 <20 行：`StickyDateHeader:1238`、`MediaKindChip:1659`、`CoverThumb:1612`、`RecommendationLoading:1853`、`AddSubscriptionSheet.kt:899 FieldLabel`(9 行)、`:866 OptionalTag`、`SubscriptionsScreen.kt:797 UnreadBadge`
- 参数过载 = 职责没拆干净的症状：`ArticleCardList:630` **14 个参数**（6 回调 + 5 个模式开关）、`ArticleAdaptiveGrid:1093` 10 参、`ArticleListItem:816` 9 参
- 中间人：`AddSubscriptionSheet.kt:95 AddSheetShell`（39 行仅转发 VM）

### 4.3 比颗粒度更致命：跨层反向依赖

- **ViewModel 依赖 Compose runtime**：`SubscriptionsViewModel.kt:5-7,189`、`AddSubscriptionViewModel.kt:3-5,132`、`FeedArticlesViewModel.kt:3-5,49-68`（7 个 `mutableStateOf` 字段）—— VM 本应平台无关
- **ViewModel 绕过 Repository 直连 DAO**：`PromptTemplatesScreen.kt:101-102,124-148`（`FeedAiProfileDao.upsert`）、`ReadingStatsScreen.kt:72,86-116`（`ArticleDao` 直查）、`RssHubSettingsScreen.kt:129-130`
- **Composable 内业务逻辑**：`FeedListScreen.kt:1060/1221/1535` 三处 `DateUtils.getRelativeTimeSpanString`；`SubscriptionsScreen.kt:907` `withoutScheme()`、`:910-913` 建 `SimpleDateFormat`；`ReaderImagePage.kt:163` 文件名拼装
- **双向数据源**：`FeedListScreen.kt:161` UI 绕过 VM 直读 `LocalListDisplay`，而注释称由 VM 写入

### 4.4 State 设计

- 最大 State：`AddSubscriptionUiState` **18 构造字段 + 5 派生 getter**；`FeedListUiState` 17 字段
- 碎片化：`ArticleDetailViewModel` 无统一 UiState，暴露 **~17 个独立 StateFlow**；`SubscriptionsViewModel` ~10 个
- UI 细节泄漏进 State：`SubscriptionsViewModel.kt:117 _expandedIds`（纯折叠态）、`:133 _selectedFeedIds`、`FeedListUiState:65 totalCount`（只为滚动条分母存在）
- 健康项：scroll position / focus / Animatable 仍留在 Compose 内，未污染 State

### 4.5 Prop Drilling

`FeedListScreen:276` → `ArticleCardList:630` → `ArticleListItem:816` → `SwipeableArticleCard:1301` → `ArticleCard:1452`，穿过 **4 层**携带 item + 5 个回调，每层重新包装 lambda。`AddSubscriptionSheet.kt` 把整个 **ViewModel 下传 3 层**——子组件不可复用、不可预览。

**本维度判定：主要矛盾是过粗（单函数峰值 446 行 / 278 行 / 239 行），不是过细。但 4.3 的跨层反向依赖对可维护性的伤害比颗粒度更直接。**

---

## 五、问题清单（按修复性价比排序）

| # | 问题 | 规模 | 收益 |
|---|---|---|---|
| 1 | 补 `Dimens.kt` / `Shapes.kt`（间距 + 圆角 + 高度 + alpha） | 1007 处 `.dp` + 130 处圆角 | 改视觉基线从全局替换变成改一处 |
| 2 | 拆 `FeedListScreen.kt`（1872 行 / 9 职责） | 1 文件 | 释放最大复杂度源，顺带消灭 4 个卡片变体 |
| 3 | 建 `SettingRow` 通用件，消灭 6 套设置行 | 17 处内联 + 6 套实现 | 设置页是最容易改出新重复的区域 |
| 4 | 消灭私有副本：`GroupActionSheet.ConfirmDialog` → `core.ui.ConfirmDialog`、`FeedListScreen.EmptyState` → `core.ui.EmptyState` | 2 处遮蔽 | 复用层从摆设变资产 |
| 5 | 抽 `ArticleCard` 单一实现 + viewMode 参数 | 6 个变体 → 1 | 同时解决重复与 prop drilling |
| 6 | ViewModel 去 Compose 依赖（`mutableStateOf` → `StateFlow`） | 3 个 VM | 恢复分层，VM 可单测 |
| 7 | ViewModel 直连 DAO 改走 Repository | 4 处 | 同上 |
| 8 | 文案迁 `strings.xml` | 834 处 | **收益最低**——除非要做多语言或文案收口，否则可无限期推迟；优先级远低于 1-7 |
| 9 | 删 `LoadingPlaceholder` 等死代码 | 1 处 | 顺手 |

---

## 复核命令

```bash
# 文案资源化率
grep -rn "stringResource\|getString(" app/src/main/java --include=*.kt | wc -l        # => 0
grep -c "<string " app/src/main/res/values/strings.xml                               # => 2

# 尺寸/圆角
grep -rnoE '([0-9]+)\.dp\b' app/src/main/java --include=*.kt | wc -l                 # => 1007
grep -rn "RoundedCornerShape(14.dp)" app/src/main/java --include=*.kt | wc -l        # => 37

# 复用层调用
grep -rn "ConfirmDialog\|EmptyState" app/src/main/java --include=*.kt
grep -rn "LoadingPlaceholder" app/src/main/java --include=*.kt                       # 仅定义，0 调用
```
