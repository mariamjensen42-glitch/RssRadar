# 正文抓取/提取的健壮化与可观测性

解决「部分文章只拿到摘要或正文不完整」。

## Status

accepted

## Context

用户反馈：部分文章只有摘要、或正文明显不完整。排查后确认**主因不在抓取，在判定**：

1. **摘要被当成正文（根因）**：`RssParser.toArticle` 取 description 与 content 中较长者为正文；
   只给摘要的 feed（RSSHub 大量路由如此）得到的是两三百字的摘要。而 `RefreshEngine` 只要
   `contentHtml != null` 就写 `contentSource = FEED`，于是 `FeedRepository.fetchFullContent` 的
   「已有正文就不抓」早退条件命中 → **详情页永远不会去抓原文**，且整个过程静默无声。
2. **抓取侧零可观测**：`ContentFetcher` 把超时/403/429/网络异常统一 `catch → null`，状态码、
   重试次数、耗时一概不知；失败与「没抓到」无法区分。
3. **提取侧零质量校验**：readability4j 给出什么就写什么，噪声、截断、付费墙一律照单全收。
4. **多页文章只抓第一页**；缓存存的是**提取后**的 HTML，提取算法升级后老缓存永远不生效。
5. 请求头只有一个自报家门的 `RssRadar/1.0` UA，对反爬站点几乎没有生还率。

ADR-0001 定的「feed 字段优先、打开详情时按需抓原文」策略不变，本 ADR 只补健壮性与可观测性。

## Considered Options

- **A. 阈值判定：feed 内容 < 300 字 → contentSource = NONE（选）**：摘要仍存进 content 列
  （列表检索与详情页兜底要用），但不再「够格当正文」，详情页照常抓原文。改动小、语义清晰。
- B. 每篇打开详情都抓原文：正文最全，但流量/电量不可控，且会用网页噪声覆盖优质 feed 全文。
- C. 只修抓取链路、不动判定：摘要型 feed 仍然永远只显示摘要，问题不解决。

提取方案：

- **A. readability4j 主路径 + jsoup 候选容器打分兜底（选）**：readability 对中文站点
  （正文拆在多层 div 里）常常只捞到片段；容器打分按「文本长度 + 段落/图片加权 − 链接密度惩罚」
  挑，两者取纯文本更长的一条。零新依赖。
- B. 换用 AI/第三方提取服务：要联网、要钱、要 key，与「离线优先」冲突。

## Consequences

### 抓取（ContentFetcher）

- 重试只对**超时 / 网络异常 / 429 / 5xx** 生效；401/403/404 不重试（重试只会浪费配额并招致更狠封禁）。
  429 尊重 `Retry-After`（上限 8s，超过就放弃）。
- 请求头换成桌面 Chrome UA（第 2、3 次尝试轮换），补 `Accept` / `Accept-Language` / `Referer`。
- 代理通过 `FetchConfig.proxy` 注入（默认 null = 跟随系统），**不给 UI 开关**——没有读取方的开关是假功能。
- 每次抓取产出 `FetchReport`（host / statusCode / attempts / pages / durationMs / bytes / contentChars /
  extractor / issue）；失败另有 `FetchFailure` 分类。
- 分页：`link[rel=next]` → 「下一页」等锚点文案 → class/id 含 next → `page` 参数 +1，
  最多 3 页，按段落文本去重后拼接（去重同时防死循环）。
- 缓存改存**原始响应**（带 `<!--rssradar-raw-v1-->` 标记）：提取算法升级后老缓存照样重跑。

### 提取（ArticleExtractor）

- 三级兜底：readability4j → jsoup 容器打分 → 去噪后的 body；取纯文本最长的一条。
- 噪声选择器剔除：导航/侧栏/页脚、广告、推荐位、评论区、分享、面包屑、分页、newsletter、
  订阅墙、`display:none` 节点。保留标题/作者/发布时间（从未去噪的原始 DOM 上取）。
- 图片统一转绝对 URL，剔除 1×1 追踪像素与占位图；`data-src` / `data-original` / `srcset` 懒加载兜底。
- 完整性判定 → `ExtractionIssue`：TOO_SHORT / NO_PARAGRAPH / DYNAMIC_RENDER / PAYWALL /
  METADATA_MISSING。低于阈值（200 字）或关键字段缺失时**仍然写入**（比空白页好），
  但 `isComplete = false`，由上层打「不完整」标记，并输出 WARN 日志。
- 输出统一过 `RssParser.sanitizeHtml` —— 顺带修掉「readability 抓来的全文没净化」的老问题。

### 存储与可观测

- DB v7 → v8：`articles.contentIncomplete`（不完整标记）+ `content_fetch_log` 表
  （link / host / statusCode / attempts / pages / ok / failure / issue / contentChars / durationMs / createdAt）。
- `FeedRepository` 每次抓取写一条 log；读取方 = 设置页「我的 → 正文抓取 → 全文抓取诊断」，
  按站点聚合显示失败数/不完整数，并列出明细（URL、状态码、重试次数、页数、原因、时间）。
- 降级保护：抓出来的正文不比现有内容长就不覆盖（避免把已有正文越抓越少）。
- 阅读页在 `contentIncomplete = true` 时显示「正文可能不完整，可查看原文」横幅。

### 已知边界（不是 bug）

- **纯 HTTP 抓不到 JS 渲染的正文**：只能识别为空壳并标记 DYNAMIC_RENDER，无法执行 JS。
- 付费墙只能靠特征词识别（正文 < 1200 字时才判），会漏也会误伤。
- 一个字都抓不到时返回 `EXTRACT_FAILED`（不写空正文），由诊断页归因。
