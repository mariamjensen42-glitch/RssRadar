# Research: 失效检测数据原料盘点（content_fetch_log / FeedProbeResult / feeds 表现状）

> Issue #79 · Part of #78（v1.0.3「失效订阅源检测与健康面板」前置调研）
> 结论先行：**现有原料不能直接支撑「连续 N 次刷新失败即标记失效」——feed 级刷新失败全程零留痕，是最大的缺口**；但 FeedProbeResult 故障分类、feeds 表加列惯例、WorkManager 每日任务模板三块都可以直接复用。

## Q1: content_fetch_log 表结构与写入点

**Entity**：`ContentFetchLogEntity` — core/data/src/main/kotlin/com/cycling/rssradar/core/data/db/AppDatabase.kt:1155-1171（表由 MIGRATION_7_8 创建，同文件 :980-1004；DB v7→v8，见 docs/adr/0012-content-fetch-robustness.md）。

字段：`id, link, host, statusCode?, attempts, pages, ok, failure?, issue?, contentChars, durationMs, createdAt`，索引 `host` + `createdAt`。`failure` 存 `FetchFailure` 枚举名，`issue` 存 `ExtractionIssue` 枚举名（成功时互为 null）。

**写入点（唯一）**：`OnDemandFetch.fetch()` — core/data/src/main/kotlin/com/cycling/rssradar/core/data/OnDemandFetch.kt:63（`toLog` :116-143）。三条规则：够格不重抓、抓短不覆盖、**每次抓取都留痕**（成功/不完整/失败三种 outcome 都写）。仅在读者打开文章触发按需抓正文时写入。

**关键缺口**：
- 粒度是「文章 link + host」，**没有 feedId 列**——只能按 host 聚合，无法按订阅源聚合。
- **feed 刷新链路完全不走这张表**：`RefreshEngine.refreshFeed()`（core/data/src/main/kotlin/com/cycling/rssradar/core/data/RefreshEngine.kt:151-187）失败时 `return@withContext false`，IOException / IllegalArgumentException 全部静默吞掉，不写任何 DB 记录。「连续 N 次刷新失败」在现有数据里**无从追溯**。
- 字段本身够用：`ok` + `failure` + `createdAt` 可聚合「最近 N 次成败 + 错误分类 + 时间戳」，DAO 已有 `historyOf(link, limit)`（AppDatabase.kt:1228-1229）与按 host 聚合的 `observeHostStats()`（:1212-1222）作参照。

**清理策略**：没有保留上限。唯一清理是用户手动「清空诊断记录」`ContentFetchLogDao.clear()`（AppDatabase.kt:1224-1225，OnDemandFetch.kt:100）。量级受限于文章打开行为，增长慢但无界。若健康面板复用此表需自带保留策略。

## Q2: FeedProbeResult 故障分类

**定义**：core/domain/src/main/kotlin/com/cycling/rssradar/core/domain/rss/FeedProbeResult.kt:9-67。sealed interface，`from(e: Throwable)`（:55-65）是全项目唯一的异常→分类映射（调用方不各写 catch 链）。现用于订阅链路（SubscriptionFlow.kt、AddSubscriptionViewModel.kt）。

全部分类（8 个）：

| 分类 | 携带信息 | 可否直接复用为「失效原因」 |
|---|---|---|
| `Valid(articleCount)` | 文章数 | —（健康态） |
| `InvalidUrl` | — | 可：URL 本身非法 |
| `InvalidFeed` | — | 可：能连上但不再是有效 feed（站点改版/关停转常规网页） |
| `NetworkError` | — | 慎用：太泛（设备侧网络问题也会落这），需连续失败消歧 |
| `HttpError(code)` | 真实状态码 | **最佳**：404=路由没了、410=永别、429=限流（不该判死）、5xx=实例挂了 |
| `Timeout(connecting)` | 是否卡在握手 | 可但需消歧：connecting=true 偏向连不上，false 偏向对端慢（注释明确「慢和连不上处置相反」） |
| `DnsError` | — | **最佳**：域名不存在 = 源已死的最强信号 |
| `CertificateError` | — | 可：自签/过期证书，Android 一律不信任 |

复用建议：`DnsError` / `HttpError(4xx 除 429)` / `CertificateError` / `InvalidFeed` 可直接作为高置信失效原因；`Timeout` / `NetworkError` / `429`/`5xx` 属低置信，需配合「连续 N 次」计数才能定性。分类枚举无需扩展，缺的只是把探测结果持久化到 feed 行上。

## Q3: feeds 表字段与 v15→v16 最小迁移增量

**Entity**：`FeedEntity` — AppDatabase.kt:27-73（url 唯一索引）。现有字段：`id, url, title, createdAt, groupName, iconUrl?, sourceType, syncEnabled, fullContentEnabled, notificationsEnabled, contentType, etag?, lastModified?`。

- **没有任何失败计数 / 失效原因 / 最后成功时间字段**。
- `etag` / `lastModified` 是 v14→v15 加的（MIGRATION_14_15，AppDatabase.kt:1112-1117），由 `RefreshEngine` 仅在 200 成功时 `updateValidators()`（RefreshEngine.kt:170），304/失败都不动——可当弱信号（etag 长期不变≈久未成功），但不可靠（源可能一直 304 正常）。

**v15→v16 最小增量**（沿用项目惯例：带默认值的可空新列，`ALTER TABLE ADD COLUMN`，零行重写，同 MIGRATION_9_10 / 14_15 模式）：

```sql
ALTER TABLE feeds ADD COLUMN consecutiveFailures INTEGER NOT NULL DEFAULT 0
ALTER TABLE feeds ADD COLUMN failureReason TEXT            -- 可空，存 FeedProbeResult 分类名 + HttpError 码
ALTER TABLE feeds ADD COLUMN lastSuccessAt INTEGER         -- 可空，存量行 null = 未知
```

**列清单常量铁律**（项目注释明确：升 schema + 写迁移 + 同步列清单，见 AiSchema.kt:23）：
- `ARTICLE_LIST_COLUMNS` — AppDatabase.kt:170-175。**只含 articles 列**，feeds 新列不涉及 articles 实体，原则上不用改；但若健康面板要把失效状态显示在文章列表卡片上，则该常量 + JOIN 别名列（各列表查询里的 `feeds.title AS feedTitle, feeds.groupName AS feedGroup, feeds.iconUrl AS feedIconUrl`，如 AppDatabase.kt:384）+ `ArticleWithFeed`（:152-157）都要同步加。
- **feeds 没有专属列清单常量**——FeedEntity 走 Room 自动映射，列表页从 FeedDao 取整行不受影响。但要全仓搜一遍手写 SQL 的 feeds 查询（列表 JOIN 的 feeds 侧只选 3 个别名列，加列不破坏它们）。

## Q4: WorkManager 现有任务与「僵尸源定期探测」挂载点

全项目 PeriodicWorkRequest 只有两处（均为 CoroutineWorker + unique periodic + UPDATE 策略重建）：

| 任务 | 位置 | 周期 | 约束 |
|---|---|---|---|
| `rssradar-auto-sync`（SyncWorker → AutoSync） | app/src/main/java/com/cycling/rssradar/sync/SyncScheduler.kt:44-51，Worker :13-28 | 用户配置（SyncStore 驱动，分钟级） | 网络 / 充电可配 |
| `AiDailyWorker`（AI 批处理） | app/src/main/java/com/cycling/rssradar/ai/AiDailyWorker.kt:79-86，reschedule :66-86 | 1 天 | UNMETERED |

挂载建议：
- **首选：仿 AiDailyWorker 新建独立每日 Worker**（如 `FeedHealthWorker`）——现有代码已是现成模板（EntryPoint 取依赖、不引 hilt-work、unique periodic、`runNow` 手动触发按钮模式 AiDailyWorker.kt:88-95 全套照抄）。独立任务不受用户同步间隔/AI 功能开关影响，探测低频（每日）对网络压力小。
- 不建议塞进 AutoSync：其注释明确「顺序即实现，调用方不得拆散」（AutoSync.kt:14-16：刷新→归档→通知），且 SyncWorker 失败整体 `Result.retry()`（SyncWorker.kt:24-27），探测混进去会被同步失败连坐。
- 注意：探测结果落 feeds 新列（Q3），Worker 与 RefreshEngine 共用 `FeedProbeResult.from()` 即可，分类逻辑全项目只有一份。

## 总结：原料够不够、缺什么

| 需求 | 现状 | 缺口 |
|---|---|---|
| 连续 N 次刷新失败判定 | **无任何 feed 级失败留痕**（RefreshEngine 静默吞错） | feeds 加 `consecutiveFailures` + 刷新链路成功/失败时增减计数 |
| 失效原因展示 | FeedProbeResult 8 分类齐全、映射单一来源 | feeds 加 `failureReason` 持久化探测结果 |
| 最后成功时间 | 无；etag/lastModified 仅成功时更新（弱信号） | feeds 加 `lastSuccessAt` |
| 历史失败聚合参照 | content_fetch_log 的 ok/failure/createdAt 模式 + observeHostStats 聚合 SQL 可参照 | 粒度不同（文章级），不可直接复用 |
| 定期探测调度 | AiDailyWorker 模板齐全 | 新建独立每日 Worker |

三列 + 一次 v15→v16 迁移 + 刷新链路两处计数埋点 + 一个新 Worker，即为最小落地路径。
