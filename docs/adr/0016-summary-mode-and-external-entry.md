# ADR-0016 正文/摘要手动切换与外部入口

- 状态：已接受
- 日期：2026-09-08
- 相关：ADR-0001（正文判定与入库）、ADR-0012（抓取健壮性）、ADR-0015（抓取可见性与相邻预取）、
  `docs/readyou-feature-comparison.md` #34

## 背景

ADR-0015 把「抓不到正文」说成了人话，但阅读页仍然只有**自动判据**：正文够不够格由
`ContentQualification` 说了算，读者没有反向出口。ReadYou 在阅读页底栏给了一个
`onFullContent` 图标，可在「全文 / 摘要」之间手动切换（`renderFullContent()` /
`renderDescriptionContent()`）。差距表第 34 项（系统分享 / 文本选择 / 翻译 intent
接入）同样挂着，RssRadar 当时零外部入口。

本次同时处理这两项。

## 决策

### 1. 摘要模式：只在两者实质不同时存在

- 新增纯函数 `canSwitchToSummary(content, summary)`：两边都非空、**去标签后**正文长度
  ≥ 摘要的 1.5 倍**且**差值 ≥ 120 字，才认为「值得切」。
- 该判据同时决定开关是否渲染。不成立时，排版面板里那一块整块不出现。
- 命中时只把正文源换成 `summary`，渲染路径（原生 / WebView）照旧，
  `BodyPlan.summaryMode` 仅用于 UI 标注。
- 作用域是**单篇瞬时**：`ArticleDetailViewModel.preferSummary` 是普通 StateFlow，
  不落库、不进 `ReadingPrefs`，换一篇文章即复位。
- 摘要态下正文区常驻一行提示「当前显示订阅源摘要，不是全文」+ 「看正文」出口。

**为什么这么保守**：ADR-0001 入库时取 description 与 content 的**较长者**，
于是大量订阅源的 `content` 就是 `summary` 本身。此时给一个切换开关，读者点下去
屏幕纹丝不动——按 UI 铁律，没有意义的按钮不该存在。ReadYou 没有这层判据
（它自己在 `Article.kt` 里写了 `@Deprecated("fullContent is the same as rawDescription")`），
这是它的倒退，不照抄。

**为什么不进阅读偏好**：这是「这篇抓坏了 / 太长先看摘要」的临时决定，
不是「以后都给我摘要」的长期偏好。写成全局偏好会让摘要型源之外的正常阅读整体退化。

### 2. 外部入口：只认链接，只做订阅

- `SharedText.extractUrl()` 从分享文本里取第一个 http(s) 地址并去掉尾随标点。
- Manifest 给 `MainActivity` 加 `ACTION_SEND` 与 `ACTION_PROCESS_TEXT`（均 `text/plain`）。
- 命中 → 打开加订阅抽屉并把地址填进 `AddSubscriptionIntent.UrlChange`（走既有校验链路）。
- 挑不出链接 → Toast 明说「这段内容里没有链接，RssRadar 只能订阅链接」，
  **不**把整段文本塞进地址栏（那只会换来一次必失败的探测）。
- `ACTION_TRANSLATE` **不做**：那需要一个独立的「翻译任意文本」界面并依赖 AI Key，
  成本与价值不匹配；PROCESS_TEXT 已覆盖「把外部文字送进 RssRadar」的主要场景。

### 3. 状态放在 Activity 而不是 savedInstanceState

外部 intent 走 `onNewIntent` 时不重建 Activity，只有 Compose 状态能让已在前台的
界面有反应；消费后立即清空，避免旋转屏幕把同一个地址重复填入、打断用户已改过的输入。

## 取舍

| 选项 | 结论 |
|---|---|
| 底栏第 7 个图标（照抄 ReadYou 位置） | 否决：底栏已有 6 个 40dp 图标，360dp 屏只剩 56dp；且这是**阅读偏好**不是**文章动作** |
| 排版面板顶部「本文」分区 | 采纳：与其余排版项同类，且不挤占底栏 |
| 摘要模式持久化到 `ReadingPrefs` | 否决：会让正常源退化（见上） |
| 全量实现 #34（含 TRANSLATE） | 否决：见上 |

## 后果

- `resolveBodyPlan` 多一个 `preferSummary` 参数（默认 false），既有调用点无需改动。
- `AddSubscriptionViewModel` 提到抽屉外创建（Activity 作用域，本来就是），
  以便外部 intent 在抽屉打开前预填。
- 新增 JVM 单测：`canSwitchToSummary` 的边界、摘要模式对渲染路径的影响、
  `SharedText.extractUrl` 的标点与空白处理。
- 差距表维护：#31（通知）早已落地，本次把状态从「差距」挪到「已有功能」；
  #21（TTS）标注为**主动不做**（对照表末尾已列入不做清单，只是表内没标）。
