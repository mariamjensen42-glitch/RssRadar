# 正文获取：原因可见、可重试，以及有限预取

对齐 ReadYou 的正文获取骨架，同时保留本项目已有的更强抓取链路。

## Status

accepted

## Context

用户反馈「部分文章只有摘要，且不知道为什么」。排查确认抓取能力本身不是瓶颈——
`ContentFetcher`（重试/UA 轮换/分页/三级兜底/去噪）与缓存**原始响应**都比 ReadYou 更强，
真正缺的是另外三件事：

1. **原因不可见**：`ArticleDetailViewModel` 用 `runCatching { onDemandFetch.fetch(id) }`
   把结果整个丢掉。抓取日志只进 `content_fetch_log`，读取方只有「我的 → 正文抓取 →
   全文抓取诊断」——读者永远不会去那儿，于是阅读页只剩一行「正在获取全文…」然后静默。
2. **没有重试入口**：失败是常态（反爬/JS 渲染），但读者没有任何可点的东西。
3. **AI 全文还原与「AI 不捏造」相冲**：`AiFeature.FULLTEXT` 声称「用模型从原始 HTML 还原正文」，
   实际喂的是 DB 里的 `content ?: summary`（摘要级 HTML）——模型要么原样吐回摘要（白烧钱），
   要么用自身知识补全正文（造谣）。它声明的 UI 出口（提示卡上的「用 AI 提取」）根本不存在。

ReadYou 的做法（源码核对）是四件事：OkHttp 抓取 → `Readability4JExtended` 提取 →
文件缓存 → `ReaderState` 状态机（`Loading` / `FullContent` / `Description` / `Error(message)`，
错误态点一下重试），外加相邻文章预取与 `ReaderWorker` 全量预抓未读。**没有 AI 参与正文生产。**

## Considered Options

- **A. 全盘照搬 ReadYou（含 `ReaderWorker` 全量预抓未读）**：语义上等于放弃「按需抓取」，
  同步后批量拉全部未读文章，流量与电量不可控，且与 `CONTEXT.md` 的
  「按需抓取 / _Avoid_: 预取」正面冲突。否决。
- **B. 只加 Error 态，不预取（选）**：把失败原因暴露出来、给出重试，预取只做紧邻两篇。
  改动集中、语义仍然自洽，翻页体验也能明显改善。
- C. B + 把 AI 全文还原修成真兜底（喂抓下来的原始 HTML）：修好输入仍有两个坑——
  原始 HTML 动辄上百 KB，截断后模型看到的可能正好是导航栏；且模型产出的正文无法验证真伪。
  正文错了是一整篇，不是一句话。否决（功能保留但默认关闭、不接线）。

## Decision

1. **抓取结果结构化**：`OnDemandFetch.fetchWithResult` 返回 `OnDemandResult`
   （`AlreadyUsable` / `FeedDisabled` / `Missing` / `Fetched` / `ShorterThanExisting` / `Failed`），
   旧 `fetch: Boolean` 委托给它，测试与既有调用不受影响。
2. **阅读页状态机**：`ContentFetchState`（`Idle` / `Loading` / `Ready` / `Incomplete(issue)` /
   `Failed(reason)`）。`Failed` 显示中文原因（`FetchFailure.label`）与「重试」按钮，
   `Incomplete` 说明是哪一类不完整（`ExtractionIssue.label`）。
   库里已带不完整标记但本次不抓时，状态也记为 `Incomplete`——不抓不等于标记不存在。
3. **相邻预取**：打开文章时顺带抓列表里紧邻的上一篇/下一篇（跳过已有正文与关闭了全文抓取的源）。
   全量后台预抓不做。
4. **缓存策略不变**：仍缓存**原始响应**（`<!--rssradar-raw-v1-->`），优于 ReadYou 缓存提取结果——
   提取算法升级后老缓存能重跑。
5. **删重复标题行**：正文里与文章标题完全相同的 `h1/h2` 一律删除（ReadYou 同款，只删文本全等的，
   宁可漏删也不误伤正文小标题）。
6. **AI 全文还原撤下**：`FULLTEXT` 默认关闭、不进阅读页按钮；枚举、prompt、产物解析全部保留，
   需要时加回一行即可恢复接线（可逆）。

## Consequences

### 正面

- 「为什么我只有摘要」有了答案：403 就说 403，源关了就说源关了，抓短了就说抓短了。
- 失败可重试，不必退出重进；相邻预取让翻页不再转圈。
- 正文不再出现标题两遍。

### 负面 / 边界

- 相邻预取会多两次抓取（仅对确实缺正文的文章），翻页快但流量略增。
- **纯 HTTP 抓不到 JS 渲染的正文**这一条没变：只能识别为空壳并标记 `DYNAMIC_RENDER`，
  原因会显示成「页面由脚本动态渲染」——解释了，但仍然拿不到内容。
- 付费墙同样只能靠特征词识别，会漏也会误伤（ADR-0012 已知边界）。
- AI 全文还原从 UI 消失后，规则提取失败的站点就是真没有正文，只有「查看原文」兜底。
