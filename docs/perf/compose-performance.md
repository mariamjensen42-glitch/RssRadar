# Compose 性能体检清单

对照一份外部《Jetpack Compose 丝滑优化指南》逐条核对 RssRadar 现状的活体清单。

**这份文档只判「合不合规」，不判「改完快不快」。** 立项时确认无真机观测、无基线数据（纯愿望驱动），因此所有结论都是静态代码审计结果，**任何一条改动是否真的提升帧率，都必须用第 6 节的 adb 测量证明**。没有数字的性能结论等于没结论。

---

## 1. 判决图例

| 符号 | 含义 |
|---|---|
| ⚠️ | **违反** —— 代码确实踩了指南的坑，有 file:line |
| ✅ | **合规** —— 已做对，无需动作 |
| ➖ | **不适用** —— 本仓库无此形态，或 strong skipping 已代劳 |
| ❌ | **指南本身错误** —— 照做会有害或无效，见附录 A |
| 👀 | **观察项** —— 无证据，等真机数据 |

---

## 2. 排序清单（只看这段也行）

### P0 —— 值得动手，且证据明确

| # | 问题 | 位置 | 判决 |
|---|---|---|---|
| 1 | 阅读页原生路用普通 `Column` 一次组合**全部**节点，非惰性 | `ArticleNativeReader.kt:145-151` | ⚠️（**release 实测后降级**：单帧 ~150ms，与文章大小弱相关，见 §8.3） |
| 2 | 详情页一次性 `collectAsState()` **15 个** StateFlow，重组扇出极高 | `ArticleDetailScreen.kt:161-175` | ⚠️ |

### P1 —— 有收益但需权衡（**release 实测后全部改判**）

| # | 问题 | 位置 | 改判 |
|---|---|---|---|
| 3 | `unreadIds` 每次快照变化 O(n) 重建 Set（累积快照每页 +30） | `FeedListScreen.kt:719-721` | ➖ **实测无收益**：release 滚动 Janky 0.81%，p95=9ms |
| 4 | 每张卡片挂一份 `animateFloatAsState` + `graphicsLayer` | `PressScale.kt:30-39` | ➖ **实测无收益**：同上 |
| 5 | `FeedListScreen` 粘性头 + 文章项是**多类型同列**，却没给 `contentType` | `FeedListScreen.kt:752/755/778` | ➖ **实测无收益**：同上 |
| 6 | `collectAsStateWithLifecycle` 0 处 / `collectAsState` 56 处，且依赖不在版本目录 | 全仓库 | 👀 维持暂缓（无证据） |

### P2 —— 已确认无需动作

列表 `key` 覆盖率极高、`animateContentSize` / `updateTransition` / 动画帧内写状态 / 组合体内做 IO **全部为零**、无 >4 层深嵌套。详见第 4 节。

---

## 3. 逐条体检

### 第 2 章 减少不必要的重组

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 2.1 `remember` / `rememberSaveable` | 组合期计算均已挂 `remember`（`dayGroups`/`scrollSlots`/`unreadIds` 全带 key）。`rememberSaveable` **0 处**，但无横竖屏重建丢失状态的已知问题 | ✅ |
| 2.2 `derivedStateOf` | 仅 3 处，全用于「滚到底加载更多」（`FeedListScreen.kt:696/1107/1691`），属官方正面范例。**没有**被滥用在同频计算上 | ✅ |
| 2.3 `@Stable` / `@Immutable` | 全仓库 0 处注解。但所有 UiState 均为 `data class` + `val` + `List/Map`（`FeedListUiState`/`SearchUiState`/`BodyPlan` 等），`SnapshotStateList` 0 处 | ➖ **零动作**（见附录 A） |
| 2.4 列表 `key` | 除 `FeedListScreen.kt:589`（分组菜单，静态小列表）外**全部有 key**，含 `stickyHeader(key=)` | ✅ |
| 2.5 拆分可组合函数 | 已按屏幕/组件拆分（`core/ui/components`、`ui/article/*`） | ✅ |
| 2.6 组合体内不做副作用 | **全部 DAO/repository 调用都在 ViewModel 的 `viewModelScope`**；`hiltViewModel()` 12 处无一处直连数据层。**最重的一处**（Jsoup 解析）已移出组合：`ReadingBody.kt:157-168` 用 `LaunchedEffect` + `withContext(Dispatchers.Default)` | ✅ |

### 第 3 章 列表与滚动

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 3.1 惰性布局 + `contentType` | 全部长列表用 `LazyColumn`/`LazyVerticalGrid`。**但 `contentType` 仅 `SubscriptionsScreen` 用了**（"feed"/"header"/"action"）；`FeedListScreen` 的 `stickyHeader` + 两个 `items` 恰是**多类型同列**，正是 `contentType` 唯一有价值的场景，却没给 | ⚠️ |
| 3.2 列表项内重量级 Modifier | `Modifier.animateItem` 已刻意调过：`fadeInSpec=null, placementSpec=null`，只留 `fadeOutSpec`（`FeedListScreen.kt:757/780`），滚动期几乎无成本。但叠加了 `pressScale` —— 每张卡片一份 `animateFloatAsState` + `graphicsLayer` + `collectIsPressedAsState`（`PressScale.kt:30-39`，调用点 `FeedListScreen.kt:929/1016/1162/1485/1722`） | ⚠️ |
| 3.3 `rememberLazyListState` + 滚动监听 | `FeedListScreen` 已 hoist（`rememberLazyListState` @690，滚动条 @1262-1288 在 **draw 阶段**读 `layoutInfo`，正确避开重组）。`SearchScreen` / `SubscriptionsScreen` 未 hoist | ✅（主路径合规） |

### 第 4 章 布局

全部合规：未见 >4 层嵌套；`Modifier.weight` 仅在 `ArticleCard` 的浅层 Row/Column；`CoverThumb` 用**固定** `.size(96.dp, 72.dp)`（注释写明刻意让 Coil 免读原图尺寸）。

### 第 5 章 动画与绘制

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 5.1/5.2 用 `graphicsLayer` 而非重组 | `pressScale` 正确走 `graphicsLayer`，`ArticleNativeReader.kt:149` 用 `graphicsLayer { alpha }` | ✅ |
| 5.3 动画回调里不写状态 | **0 处** | ✅ |
| `animateContentSize` | **0 处**（指南明确点名的反模式，本来就没有） | ✅ |
| 列表项内 `animateItem` | 已调过，见 3.2 | ⚠️（轻微） |

### 第 6 章 图片

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 6.1 用 Coil + `AsyncImage` | Coil **3.3.0**，`AsyncImage` 用于 `RadarImage`/`FeedIcon`/`ArticleNativeReader`/`ReaderImagePage`。`SubcomposeAsyncImage` **0 处**（`FeedListScreen.kt:1609` 注释专门说明选 `AsyncImage` 因其无子组合） | ✅ |
| 6.2 合适尺寸 | `MAX_DECODE_PIXELS = 5_000_000` + `clampDecodeSize`（`ArticleNativeReader.kt:178/185`）；列表缩略图固定 dp | ✅ |
| 6.3 `rememberAsyncImagePainter` | 未使用，且**不应该用** | ❌ 见附录 A |
| 自定义 ImageLoader 缓存策略 | **无** `ImageLoader` 配置（无 `SingletonImageLoader`），走 Coil 默认内存/磁盘缓存 | 👀 |

### 第 7 章 分析工具

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 7.1 Compose 编译器指标 | `composeCompiler {}` / `reportsDestination` / `metricsDestination` **全仓库 0 处** | ⚠️ 存在性缺口 |
| 7.2 Layout Inspector | 无代码依赖，需人工开 | ⚠️ |
| 7.3 Macrobenchmark | 无 `:benchmark` 模块（`settings.gradle.kts` 只有 `:app` + 4 core） | ⚠️ |
| 7.4 Baseline Profile | 无 `baseline-prof.txt`、无 `baselineProfile {}`，CI 只有一条单测命令 | ⚠️ 存在性缺口 |

**这四项的门槛是 gradle，不是智商。** 开发机禁跑 gradle，但用户用 Android Studio 构建安装后，**第 6 节的 adb 测量不依赖 gradle**，是可行的替代路径。

### 第 8 章 状态管理

| 指南条目 | RssRadar 现状 | 判决 |
|---|---|---|
| 8.1 单向数据流 + 不可变 | ADR-0003 MVI 契约，UiState 全不可变 | ✅ |
| 8.2 不暴露可变集合 | `SnapshotStateList` / `mutableStateListOf` **0 处** | ✅ |
| 8.3 `collectAsStateWithLifecycle` | **0 处**，56 处 `collectAsState`；`lifecycle-runtime-compose` **不在** `libs.versions.toml`（只有 `lifecycle-runtime-ktx 2.11.0` 与 `lifecycle-viewmodel-compose`） | 👀 **暂缓，理由见下** |

**暂缓理由**：单 Activity + Navigation，离开屏幕即 dispose，真实收益窗口只剩「App 退后台」一种；代价是新增一个无法在本地验证的 gradle 依赖 + 改 56 处。风险收益比难看。**等真机证据再动。**

### 第 9 章 其他

`Modifier` 链固定、`rememberUpdatedState` 14 处、`MovableContentOf` 未用（无此场景）——均合规或无需引入。

---

## 4. 两个 P0 的详细说明

### P0-1 阅读页原生路非惰性

`ArticleNativeReader.kt:145-151`：

```kotlin
Column(modifier = modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
    nodes.forEach { node -> RenderNode(node, style, image, onLinkClick, onImageClick) }
}
```

节点上限 `MAX_BLOCKS = 800` / `MAX_DEPTH = 24`（`ReadingNodes.kt:29,32`）。**一篇长文可组合出数千个 composable，全部一次性组合、布局、绘制。**

这是全仓库**唯一**符合指南第 3 章「长列表必须惰性」形态却没用惰性的地方，也是这份外部指南**完全没覆盖**的场景（它只教你优化 `LazyColumn`）。

**不建议直接改成 `LazyColumn`**：ADR-0009 的语义前提是「一篇文章 = 一个连续文档」，列化会撬动阅读进度、锚点跳转、全选复制三件事。先测量，再决定是列化还是降低 `MAX_BLOCKS` 阈值。

### P0-2 详情页 15 个独立状态源

`ArticleDetailScreen.kt:161-175` 连续 collect 15 个 StateFlow：`article` / `initialLoadDone` / `isFetchingContent` / `contentFetch` / `preferSummary` / `aiSummaryState` / `translationState` / `neighbors` / `readingPrefs` / `linkShare` / `aiArtifacts` / `aiRunning` / `aiMessage` / `aiEnabledFeatures` / `aiKeyConfigured`。

任一发射 → 整个页面作用域重组 → 连带正文区重绘。ADR-0003 的 `UiState` 契约本就要求聚合为单一不可变快照（CONTEXT.md 已注明「其余 VM 仍是碎片 StateFlow，由候选 C 统一」）。**这是架构欠账，不是性能技巧问题。**

---

## 5. 附录 A：指南本身的错误条目（照做有害或无效）

| 指南原文 | 事实 |
|---|---|
| 「用 `@Suppress("UNUSED_EXPRESSION")` 防止意外重组」 | **纯属胡说**。它只压制 Kotlin 对「表达式结果未被使用」的诊断，不影响字节码，与重组零关系 |
| 「用 `@Immutable` 帮助编译器跳过重组」 | 自 Kotlin 2.0.20 起 **strong skipping 默认开启**（本项目 Kotlin 2.2.10）。官方《Fix stability issues》把注解排在最后一位，并比喻为 `!!`：覆写编译器推断，可能漏掉本该发生的重组。另：`enableStrongSkippingMode` 布尔项已废弃，改走 `ComposeFeatureFlag.StrongSkipping.disabled()` |
| 「用 `Modifier.offset` 代替 `padding` 避免重新布局」 | 措辞错误。`offset` 仍走 placement。真正的区别是 `offset(x,y)` 在**组合阶段**读 state（每帧重组）vs `offset { }` 在**布局阶段**读（跳过组合，但有额外开销，只对每帧变化的值划算） |
| 「用 `rememberAsyncImagePainter` 缓存」当默认建议 | Coil 3 官方明确优先 `AsyncImage`（内部就是该 painter）。painter 版**不探测屏幕尺寸、按原图尺寸加载**，是反向优化；`SubcomposeAsyncImage` 官方明确不宜用于 LazyList |
| 「`contentType` 帮助 Compose 复用组合」 | 真，但**只在多类型同列时有意义**。单一类型列表加它零收益 |

### 指南遗漏的重大项

- **先测量再优化**，且必须 **release + R8** 才测得准
- LazyList `key = {}`（官方口径影响比 `contentType` 更大）
- `Modifier.drawWithCache`（官方点明「绘制文本开销高，drawWithCache 可缓存对象」）
- 字体预热：`createFontFamilyResolver(context)` + `FontFamily.Resolver.preload()`
- 反直觉数据点：Google 自己的 Text benchmark 显示 `Text` / TextMeasurer / drawText 帧耗时几乎同级，**subcomposition 最差**。所以「换 `BasicText`/`drawText` 提速」无数据支撑

---

## 6. 附录 B：核销记录（本次审计中撤回的错误结论）

审计过程中有两条结论被推翻，留档以免将来重复踩坑：

1. **「`BodyMode.kt:96` 在组合体内跑 Jsoup 解析」——错。** `resolveBodyPlan` 是纯函数（`BodyMode.kt:62`，无 `@Composable`），调用点在 `ReadingBody.kt:157-168` 的 `LaunchedEffect` + `withContext(Dispatchers.Default)`，**既不在组合内，也不在主线程**。只看定义不看调用点就会得出这条错误结论。
2. **「第 7 节整套工具链不可用，无法验收」——错。** `docs/performance-measurement.md` 全套是 adb 命令，不依赖 gradle。用户用 Android Studio 构建安装后，测量可自动执行。

---

## 7. 怎么验（adb，不依赖 gradle）

口径沿用 `docs/performance-measurement.md`（冷启动 < 1500 ms、Janky frames < 5%、每场景 3 次取中位数）。

### 7.1 现有手册已有

冷启动 `TotalTime`、滚动 `Janky frames`、DB 查询 `EXPLAIN QUERY PLAN`、内存 `TOTAL PSS`。

### 7.2 手册缺失：「打开文章」场景（P0-1 的验证前提）

现有手册四个场景（冷启动 / 滚动 / DB / 内存）**没有一个覆盖「点开一篇文章到首帧」**，而最大的嫌疑对象恰好活在这个洞里。补两条：

```bash
# 取一篇真实文章 id（需要 debuggable 包）
adb shell run-as com.cycling.rssradar \
  sqlite3 databases/rssradar.db "SELECT id FROM articles ORDER BY publishedAt DESC LIMIT 1;"

# 热启动直达文章（外部深链 rssradar://article/{id}，见 AndroidManifest.xml:49-59）
adb shell dumpsys gfxinfo com.cycling.rssradar reset
adb shell am start -W -a android.intent.action.VIEW -d "rssradar://article/<ID>" com.cycling.rssradar
sleep 3
adb shell dumpsys gfxinfo com.cycling.rssradar
```

看 `Janky frames` 百分比与 `Slow rendering` 段。**建议阈值与滚动一致：Janky frames < 5%。**

> 备选：若单 Activity 下 `am start -W` 的 `TotalTime` 不可靠（intent 投递给已存在 Activity），改用 `input tap` 点首张卡片，再取 gfxinfo 窗口。设备上实测后定。

### 7.3 建议补进手册

本节内容待真机跑通后并入 `docs/performance-measurement.md`，避免仓库里出现两份性能文档。

---

## 8. 实测记录（2026-09-09）

**设备**：Redmi Note 10 Pro（chopin），Android 13
**规模**：feeds = 618 / articles = 3197 / unread = 3090 —— **低于手册门槛（1000 / 30000），数字仅供参考**
**手法**：`input swipe` 连续 30 次 200ms 猛滑（比人手激进）；每场景 3 次取中位数；打开文章走深链 `rssradar://article/{id}` + `gfxinfo reset` 窗口法
**测试开了 2 篇文章**（会写 `isRead` / `lastOpenedAt`）；`list_mark_read_on_scroll=false`，未动未读数据
`run-as sqlite3` 被此设备 ROM 拒绝，改用 `exec-out cat` 拉 DB 三件套到本地查询

### 8.1 debug 包（Android Studio 直装）

| 场景 | 结果 |
|---|---|
| 冷启动 `TotalTime` | 592 / 628 / 651 ms，中位 **628 ms** |
| 信息流滚动 12s | Janky **48.1% / 35.8% / 23.2%**，中位 35.8%；p90 = 23-40ms |
| 打开普通文（5KB） | `TotalTime` ≈ 240ms；过渡帧 p95 = 150ms |
| 打开长文（122KB） | `TotalTime` ≈ 230ms；**单帧最高 750ms**（首开），复开 200-250ms |
| 长文内部滚动 | Janky **1.35%**，p95 = 10ms |
| PSS | 218 MB |

### 8.2 release 包（同日复测，同一设备同一手法）

| 场景 | 结果 | 判定 |
|---|---|---|
| 冷启动 `TotalTime` | 269 / 271 / 310 ms，中位 **286 ms** | ✅ |
| 信息流滚动 12s | Janky **0.96% / 0.81% / 0.41%**，中位 **0.81%**；p50 = 5-7ms，p90 = 7-9ms，p95 = **9ms** | ✅ 远优于 5% 线 |
| 打开普通文（5KB） | `TotalTime` ≈ 140ms；过渡帧 p95 = **109-121ms** | ⚠️ 单帧重 |
| 打开长文（122KB） | `TotalTime` ≈ 160ms；过渡帧 p95 = **133-150ms** | ⚠️ 单帧重 |
| 长文内部滚动 | Janky **0.29%**，p50 = p90 = p95 = **5ms** | ✅ 完美 |
| PSS | **145 MB** | ✅ |

### 8.3 最终结论（推翻 8.1 的解读）

1. **信息流滚动根本不卡。** debug 包的 35.8% 几乎全是包体假象——release 下 0.81%、p95=9ms，比手册 5% 的合格线好一个数量级。**§2 里 P1-3/4/5（unreadIds、pressScale、contentType）全部改判「实测无收益，不做」。** 这就是「先测量再优化」的价值：那份外部指南花三章教的东西，在这个 App 上量出来全是噪声。
2. **P0-1 降级。** 长文打开的单帧从 debug 的 750ms 降到 release 的 ~150ms，且**普通文（5KB）打开也要 109-121ms**——重帧与文章大小弱相关，说明大头不是节点数量，而是详情页首帧的固定成本（导航过渡 + 首次组合 + 资源初始化）。`MAX_BLOCKS=800` 的 Column 目前不是用户可感知的瓶颈。
3. **「打开文章 ~150ms 单帧」是测量伪影，已结案。** atrace 分解显示那 150ms = Activity 重建 ~60ms（`am start` 深链在已有任务上重建 MainActivity：performCreate + activityDestroy + 两次 relayoutWindow）+ 后台 young GC ~65ms + 最重 traversal 帧 ~47ms。**真实点击路径**（点卡片）实测 Janky 5.8%、p95 = **13ms**，且与文章大小无关（5KB 与 122KB 同量级）。未达修复门槛，不动。
4. 顺带的真实观察：**打开文章撞上后台同步刷列表时**，采样窗口内 102 帧均匀卡在 18-20ms（约 40fps，Janky 74.5%）——同步落库触发列表更新的那几秒整体变慢。单次采样未复现，记为已知现象；若用户反馈「打开文章时偶尔整屏发涩」，先查同步窗口与 UI 更新的叠加。
5. 内存 release 145MB vs debug 218MB，均低于 250MB 线；「进出详情 20 次后回落」仍未复测。
6. 测量方法学（深链伪影、熄屏陷阱、debug 包失真、同步污染）已并入 `docs/performance-measurement.md` §2 陷阱清单与新增 §2.5「打开文章」场景——两份文档不再各说各话。

### 教训

这条链路本身就是本仓库「没有数字的性能结论等于没结论」的活案例：静态审计给出的 P0/P1 排序，被一轮 release 实测砍掉了四分之三。**静态体检可以告诉你「哪里可疑」，只有测量能告诉你「哪里值得修」。**
