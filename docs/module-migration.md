# 模块迁移计划：:app → :core:model / :core:domain

> **状态：P0 + P1 已完成（2026-09-04）。** 已迁：RouteCatalog / RssHubRoute 模型（RssHubRoutes 拆到 domain）/ MarkAsReadCondition / GROUP_* 常量（→ core.model.FeedGroups）；RoutePath / HttpFetcher 全栈（含异常类）/ RetryOnSlowResponse / FeedProbeResult / RecommendationScoring（→ core.domain）。配套测试随迁，CI 增加 `:core:model:test :core:domain:test`。静态验证：133 文件 0 error，迁移动的 48 个单测 JVM 全绿。

> 2026-09-04 基于全量源码依赖扫描制定。原则：一次只迁纯依赖（零 Android / 零 Room），包名换成 `com.cycling.rssradar.core.*`，app 侧 import 逐个更新。

## 候选文件依赖扫描结论

| 文件 | 行数 | 当前依赖 | 判定 |
|---|---|---|---|
| `data/rsshub/RouteCatalog.kt` | 86 | 无 import（纯 enum/data class/object） | ✅ 直接迁 model |
| `data/rsshub/RssHubRoute.kt` | 207 | `GROUP_*` 常量（在 AppDatabase.kt 顶部） | ✅ 迁 model，常量随迁 |
| `data/store/MarkAsRead.kt` | — | 无 | ✅ 迁 model |
| `data/rsshub/RoutePath.kt` | 163 | 仅 `java.net.URLEncoder` | ✅ 迁 domain（URL 构建逻辑） |
| `data/RecommendationScoring.kt` | 354 | 仅 `kotlin.math` | ✅ 迁 domain（纯打分逻辑） |
| `data/parser/FeedProbeResult.kt` | 69 | `HttpStatusException`/`HttpTimeoutException`（定义在 HttpFetcher.kt） | ✅ 连异常类一起迁 domain |
| `data/rsshub/RouteCatalogSlimmer.kt` | — | kotlinx-serialization | ⏸ P2：domain 加 serialization 插件后迁 |
| `data/rss/HttpFetcher.kt` 栈 | — | 仅 `java.net.HttpURLConnection`（JVM） | ⏸ P2：整个 HTTP 栈可迁 domain |
| `ui/article/ReadingNodes.kt` / `MathMl.kt` / `data/opml/*` | — | jsoup（JVM 库，非 Android） | ⏸ P3 可选：domain 加 jsoup 依赖 |
| 其余（FeedRepository、ArticleCleaner、store/*、db/*） | — | Room / Android / Hilt | ❌ 留 :app data 层 |

## 阶段

### P0 — model 先行（零新依赖）
1. 新建 `:core:model` 包下三个文件：`RouteCatalog.kt`、`RssHubRoute.kt`、`MarkAsRead.kt`
2. 把 `GROUP_TECH/GROUP_DEV/GROUP_DESIGN` 三个常量从 `AppDatabase.kt` 抽到 model（建议 `GroupConsts.kt` 或直接放 RssHubRoute.kt），`AppDatabase.kt` 改 import
3. app 侧全部引用改 import `com.cycling.rssradar.core.model.*`
4. 验证：`scripts/check-kotlin.py --files` 把 model 源目录加进去编译

### P1 — domain 纯逻辑
1. `RoutePath.kt`、`RecommendationScoring.kt` → `:core:domain`
2. 从 `HttpFetcher.kt` 摘出 `HttpStatusException`、`HttpTimeoutException`（含 `Phase` enum）→ domain；`FeedProbeResult` + `from()` → domain
3. domain 模块 coroutines 依赖改 `api`（如果迁入类的公有签名带 suspend/Flow）
4. app import 更新；check-kotlin 验证

### P2 — 需要 domain 加依赖的批次（每项单独评估）
- serialization 插件 + json → `RouteCatalogSlimmer`
- 无需新增（JDK 自带）→ `HttpFetcher` 全栈

### P3 — 可选
- jsoup → `OpmlParser`、`ReadingNodes`、`MathMl`（注意 ReadingNodes 虽纯 JVM 但包名在 ui 下，迁移即换包）

## 迁移机制约定
- 包名必须换 `com.cycling.rssradar.core.model` / `.core.domain`（不留旧包别名）
- app 只 `implementation(project(...))`；类型跨签名透出时用 `api`
- 单测：迁入文件如有配套 test 一起搬（反引号中文测试名规则不变）
- 禁 gradle：每阶段用 `scripts/check-kotlin.py --files <model/domain 源> <app 依赖方>` 静态验证；最终由 CI `:app:testDebugUnitTest` 兜底
- 每阶段独立 commit（dev 分支），攒批 PR
