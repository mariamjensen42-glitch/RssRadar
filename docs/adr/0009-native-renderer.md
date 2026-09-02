# 阅读页双渲染器：WebView + 原生 Compose 二选一

阅读页正文提供两套渲染器，设置页手动全局切换（WebView / 原生 Compose），全应用生效。

## Status

accepted

## Context

阅读页正文用 WebView 渲染净化后的 styled-HTML。原始痛点：当详情页带 AI 摘要卡片、用户在文章内滑动时，页面在闪烁——根因是 WebView 父组合（ReadingBody）因顶栏 `showTitle` 翻转而重组，每次重组都会触发 `AndroidView.update` 重新 `loadDataWithBaseURL`，整页 HTML 每帧重载（issue #59）。

闪烁修了（用非 State 容器记住已加载 HTML，仅内容真变才 reload），但暴露了一个更根本的需求：用户希望正文可用原生 Compose 渲染——纯文字文章没有 WebView 的闪烁/内存包袱，文本天然可选中，深色主题跟随系统更稳定。于是引入第二套渲染器，让用户按需选择。

## Considered Options

- **A. 设置页手动全局开关（选）**：仿 ReadYou，WebView / 原生二选一，SharedPreferences 存，全应用生效。最少表面积、行为可预测、调试简单。
- B. 按文章/源自动切换（否决）：表面积大、行为不可预测、难以排查。
- C. 仅翻译/纯文字走原生（否决）：半自动对用户体验割裂。

原生路的技术选型：

- **A. 手写 jsoup→Compose 转换器（选）**：项目已含 `org.jsoup:jsoup:1.18.3`（Apache-2.0），把 sanitize 后的正文 HTML 解析成 DOM 再手写 Compose 文本树。零新依赖、许可证干净。
- B. 引入第三方 Compose-HTML 库（否决）：`com.mohamedrejeb.ksoup:ksoup-compose` 经核实**从未在 Maven Central 发版**，不可用；其余同类库要么停更、要么许可/体积不合适。
- C. 抄 Feeder 的 GPL 转换器（否决）：许可证不兼容，直接排除。

## Consequences

- 默认渲染器 = **WEBVIEW**，原生为 opt-in。原因：原生路对表格/视频/内联样式有已知退化（见下），WebView 仍是最稳的通用方案。
- 原生路必须自己重做、库给不了的两块：
  - **深色主题**：WebView 靠注入 CSS 主题色；原生路读 `RssRadarPalette` 的 `TextPrimary/Link/Surface2/Divider/Accent` 等 getter，直接映射到 Compose `TextStyle`。
  - **媒体占位卡**：`sanitizeHtml` 已把 `iframe/video` 转成 `<a class="media-card" href>`（ADR 沿用，原生不认 iframe）。原生路用 `Surface` 卡片（▶ + 标签·域名）重画，点击经 `onLinkClick` → `openUrl` 外开。
- 原生路额外收益：图片用 Coil `AsyncImage`（与 FeedIcon 同款 coil3）懒加载，不进 WebView 全高堆——反而避开 ADR-0007 的整页 OOM 风险；文本天然可选中；译文仍走 WebView（原生不渲染译文模板）。
- 原生路已知退化（与决策一致，非 bug）：表格只给基础网格、内联样式/动画不还原、复杂 CSS 排版不如 WebView。
- 链接点击统一走 `LocalUriHandler` 注入的 handler → `onLinkClick`，与 WebView 路的外链接管行为一致。
- 实现文件：`ui/article/ReadingNodes.kt`（HTML → 中间树，纯 JVM 可测）、`ui/article/ArticleNativeReader.kt`（中间树 → Composable）、`data/store/ReadingRendererStore.kt`（偏好）、经 `CompositionLocalRoot` 注入 `LocalReadingRenderer`、设置入口在 `ReadingStyleSheet`、阅读页 `ReadingBody.BodyContent` 按解析结果二选一。

## 解析的健壮性约束（后补）

解析与渲染分离：解析是纯 JVM 函数（只依赖 jsoup），单测直接覆盖；渲染只剩「树 → Composable」的直译。
输入名义上是 `sanitizeHtml` 的产物，但**实际不一定**——按需抓全文（`ContentFetcher` + readability4j）写入的
`article.content` 未过 `sanitizeHtml`，可能带相对路径、残留 iframe、任意深度嵌套。因此解析端自建三道闸：

- **深度上限**（`MAX_DEPTH = 24`）：畸形嵌套不再把递归打成 `StackOverflowError`（Error 不是 Exception，兜不住只能在源头截断）。
- **节点总量上限**（`MAX_BLOCKS = 800`）：超长正文只渲染前 N 块，避免一次组合上千个 `Text` 钉死主线程。
- **URL 一律过 `absoluteUrl`**：只放行 http(s)，协议相对补 https，`mailto:`/`javascript:`/相对路径降级为纯文本或不渲染。

**空树必须回退 WebView**：解析吞掉一切异常并返回空列表；`ReadingBody` 在空树时不走原生路，否则解析一无所获的文章会显示成空白页。

## 数学公式与上下标（后补）

原生路渲染公式（WebView 路不受影响）：

- **来源与可行性**（探针实测）：`sanitizeHtml` 剥掉所有属性（class 全丢），但**保留元素结构**——
  KaTeX 与 MathJax(CHTML) 都会在页面里留一份 `<math>` 辅助标记，`msup`/`mfrac`/`mi`/`mn`/`mo`
  过完 sanitize 还在。因此认 `<math>` 标签、不认 class。MathJax 的 **SVG 输出模式拿不到**
  （sanitize 直接删 `<svg>`），那种页面只能看降级文本。
- **解析**在 `MathMl.kt`（纯 JVM，可测）：MathML 子集 → `MathSpan(text, script, italic)` 片段序列。
  上下标 → 真上/下标（Compose `BaselineShift`）；分式 → `分子⁄分母`（U+2044，需要时补括号）；
  根号 → `√(…)`；表格 → 换行+空格；`mphantom` 丢弃；`semantics` 只取本体、丢 `annotation`；
  不认识的标签一律递归取内容——宁可线性化得难看，不能把内容吞掉。
- **行内 vs 块级**：`<p>文本<math>…</math></p>` 保持行内（InlineMath 混在段落里）；
  整段只有一个公式（WordPress/KaTeX 独立公式块的常态）→ 升级 `NodeMath` 居中块。
- **公式图可见性**：codecogs / chart.apis / mathjax 这类 CDN 吐的是**黑字透明底**的图，
  深色主题下等于隐形。按 src/alt 的 LaTeX 特征识别成 `NodeImage(isFormula=true)`，
  渲染端垫一层 `Surface2` 底色（clip 之内，保圆角）。
- `<sup>`/`<sub>` 同样走 `BaselineShift`（此前完全丢失）。

已知边界：这是「可读的线性化」，不是 KaTeX 级排版；公式源（LaTeX 文本 `$…$`）不做识别渲染。
