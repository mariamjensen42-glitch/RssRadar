# OPML 导入采用盲导（不联网校验），标题与分组均取自 OPML 本身

用户需要把旧阅读器的订阅库一次性搬进 RssRadar。OPML 是跨阅读器交换订阅源的事实标准。导入策略上存在「逐源联网校验」与「盲导（解析后直接入库，不联网）」两条路，决定采用**盲导**。

## Status

accepted

## Considered Options

**导入策略**

- 逐源 probe（复用 `probeFeed` 联网校验 + 解析标题）：100 个源 = 100 次网络请求、分钟级耗时、需进度 UI；用户诉求是「快和全地把旧库搬过来」，当时校验的价值极低——死源在既有刷新链路里本来就静默跳过，盲导收下死源零损失。否决。
- 盲导（选）：纯本地 XML 解析，秒级完成；文章内容由导入后的定向刷新（复用 `refreshFeed` 静默失败语义）补齐。

**feed 标题来源**

- 等抓取后用 feed 自带标题：与盲导矛盾（不联网就没有抓取）；且 OPML 的 `text`/`title` 是用户在旧阅读器里亲手整理过的名字。否决。
- OPML `text` 属性优先、缺省回退 URL（选）。注意 `refreshFeed` 只更新文章不更新 feed 标题，该选择是持久性的。

**分组映射**

- 只取一级文件夹名：丢多级层级信息。否决。
- 多级用 `/` 拼接（如 `技术/后端`）成一个分组名（选）：信息无损，UI 上就是普通分组名；新分组自动注册进 GroupStore；无文件夹包裹的源归默认分组。

**重复处理**

- 重复时覆盖更新分组：让幂等导入带副作用。否决。
- 沿用规范化 URL 精确匹配（`normalizeUrl` → `findIdByUrl`），重复**跳过并计数**（选）：同一 OPML 导两次 = 第二次全跳过。

**范围**

- 导入导出成对实现：导出（分组写回 outline、RSSHub 路由地址还原）是独立决策集，混入会膨胀。否决——本期只做导入，导出另立 issue。

## Consequences

- `OpmlParser` 手写 `XmlPullParser`（Android 内置，零新依赖），放 `data/opml/`，纯函数：`(InputStream) -> List<OpmlEntry>`；根元素非 `<opml>` 视为无效文件。
- `FeedRepository` 新增 `importOpml`（批量盲导，返回导入数/跳过数/新源 id）与 `refreshFeeds(feedIds)`（定向刷新，只刷新导入的源）。
- 导入完成后自动后台定向刷新一轮，消除「导完信息流是空的」断点；导入结果与刷新提示走既有 `uiMessage` Snackbar。
- OPML 无法判定源是否 RSSHub 路由，一律按 `SOURCE_TYPE_RSS` 入库。
- 入口：订阅管理页顶栏 `FileUp` 图标 → SAF 文件选择器（mime 放宽至 `["text/*", "application/xml", "application/octet-stream"]`，规避文件管理器 mime 标注不一致）。
- 解析不出 `xmlUrl` 的 outline 行静默忽略；事件走 `SubscriptionsIntent.ImportOpml`（ADR-0003 契约）。
- `OpmlParser` 配单测，对标 `RssParserTest`。
