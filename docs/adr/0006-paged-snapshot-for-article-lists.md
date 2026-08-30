# 信息流分页快照：手写 LIMIT/OFFSET，不引 Paging3 / room-paging

信息流的四个 tab（全部 / 未读 / 收藏 / 稍后读）从"全量 observe 流"整体切换为"分页快照"：LIMIT/OFFSET 每页 30 条、滚动到底预加载、列表数据是 ViewModel 持有的快照而非数据库实时投影。

## Status

accepted

## Context

规模现实：订阅源 1000+、文章数万条。原实现里"全部"tab 分页，其余三个 tab 用 `stateIn(WhileSubscribed)` 的全量 observe 流。两类真实崩溃促成了重设计：

1. **OOM**：全量查询把每篇文章的 `content`/`contentText` 全文 HTML 物化进 Java 堆，数万行 × 几十 KB 直接打满 256MB；
2. **CursorWindow 越窗**：单窗口只装得下几百行，数万行的游标在 Room 2.8 KMP 驱动下逐行映射越窗访问崩溃。

更本质的问题：任何 DB 写（标已读、刷新回填）都会使 observe 流失效并触发**整表重查**——高频写场景下这是持续性的内存与主线程税。

## Considered Options

- **保留全量 observe 流（否决）**：即使裁剪投影，"每次写失效 → 全表重查"的模型在数万行规模下不可行。
- **Paging3 + room-paging（否决）**：能白拿加载态，但 (a) Room 的 PagingSource 在表写入时整组失效重载，与全量刷新期间的高频写入正面冲突；(b) `PagingData` 不可变，"卡片原地更新（标已读/收藏立刻变样式）""删除后本地移除"没有等价物，需自维护 Patch Map 再 combine，复杂度净增；(c) 200~300 行级的迁移加重新调试，解决的是当时没有的问题。
- **手写 LIMIT/OFFSET 分页快照（选）**：列表是 ViewModel 里的普通 `List` 快照；卡片状态更新走 `mutateLocal` 原地改快照 + 落库，**不依赖 DB 失效重查**；切 tab / 下拉刷新 / 撤销删除时显式重载首页。列表查询统一走轻量投影（`ARTICLE_LIST_COLUMNS`，剔除 `content`/`contentText` 两列全文，`aiSummary` 入库截断 2000 字符），详情页仍走 `getWithFeed` 的 `SELECT *`。

## Consequences

- **快照语义**：列表内对文章状态的操作（如未读 tab 标已读）当下只变样式不把条目移出列表，切 tab 或刷新后才与库一致；未读数徽标走 COUNT 流仍实时。
- 每页 30 条，CursorWindow 永远只装一小页，越窗/OOM 路径物理消失。
- OFFSET 深层页是 O(offset) 扫描，数万条量级毫秒级无感；若未来翻页卡顿，升级方向是 keyset（按 `publishedAt` 游标），届时再评估是否顺手上 Paging3。
- 新列表场景（如订阅源文章列表）沿用同一管线：`ARTICLE_LIST_COLUMNS` 分页查询 + 快照 + `mutateLocal`。
