# 采用 MVI 密封 Intent + onIntent 统一 ViewModel 事件面

RssRadar 的 5 个 ViewModel 当前用散装 `fun` 方法接收事件（`toggleStarred` / `selectTab` / `submit` …），无统一契约，且 `uiMessage`（Snackbar）在部分 VM 漏在 `UiState` 外。决定为每个 ViewModel 引入 per-VM 的 `sealed interface XxxIntent`，以单一 `onIntent(intent)` 取代全部散装事件方法，并引入轻量 `MviViewModel<I>` 接口统一契约。这是 MVI 单向数据流的第一步（候选 A），为候选 B（一次性 Effect 通道）与候选 C（单一 `UiState`）铺路。

## Status

accepted

## Considered Options

**事件面形态**

- 维持散装 `fun` 事件方法（现状）：调用点分散、无类型化事件目录、难测、与官方 MVI 单向数据流不符。否决。
- 密封 Intent + `onIntent`（选）：事件即数据，单一入口集中分发；与 Compose `onIntent` 官方范式一致；`when(intent)` 穷尽性检查编译期兜底漏写分支。

**共享接口是否含 state**

- `MviViewModel<I, S>` 含 `val uiState: StateFlow<S>`（双参接口）：会在碎片 VM 上强制聚合 `UiState`，提前侵入候选 C。否决——本期接口只含事件面 `MviViewModel<I> { fun onIntent(i: I) }`。
- 不为 lifecycle 引入抽象基类（`@HiltViewModel` 已管构造）。

**`uiMessage` / `ConsumeMessage` 归属**

- 整条甩给候选 B（Effect 通道）：A 便无法收口已存在的 `onMessageShown()`。否决。
- A 把 `onMessageShown()` 收为 `Intent.ConsumeMessage`（选），但**不改 `uiMessage` 承载通道**（仍在 `UiState` 内），通道迁移归候选 B。

## Consequences

- 5 个 VM 全部 `implements MviViewModel<XxxIntent>`；散装事件方法改写为 `sealed interface XxxIntent` 的 `data object` / `data class` 条目。
- `toggleStarred` 类事件去掉 UI 传入的 `current` 参数，VM 从自身 state 翻转（消除「UI 把当前值回传 VM」反模式）。
- `load(articleId)` / 初始拉取保留在 `init {}`（从 nav args / `savedStateHandle` 读），不进 Intent；`reset()` / `onBackToCatalog` 等用户触发的导航事件进 Intent。
- 纯函数（`filterByGroup`）与状态 producer（`getFeed`）保持 `fun`，不进密封 Intent。
- 6 个 screen 调用点同步改写为 `vm.onIntent(XxxIntent.Xxx(...))`，与 VM 同 PR 合并、关同一 issue。
- 不引入 ViewModel 测试（纯重构无行为变更）；契约测试挂到候选 C（单一 `UiState` 可断言）后。
- 实现遵循「一 issue 一 PR」：建议新建 issue（如 #34）跟踪，关联本 ADR；与候选 B/C 解耦，不回头补。
