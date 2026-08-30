# RSSHub 路由发现：内置全量路由快照 + 可在线更新

路由目录不再硬编码 14 条，改为内置 RSSHub 官方全量路由元数据（1979 个命名空间 / 3800 条路由 / 25 个分类）的精简快照，并支持联网更新。加订阅抽屉的目录步因此变成真正的「feed 发现」入口：搜索、分类筛选、按热度排序、点官方示例一键填参。

## Status

accepted

## Context

「以 RSSHub 为核心」是项目定位，但路由目录一直只有 14 条手写死数据（`RssHubRoutes.all`）——写死等于把 RSSHub 最核心的价值（几千条路由）挡在门外。用户想订阅一个没被内置的源，就只能自己去翻文档拼 URL，阅读器本身不提供任何发现能力。

顺带解决的历史包袱：RSSHub 实例地址曾写死 `DEFAULT_HOST`，官方实例在部分网络环境不可达。这部分已由 `RssHubInstanceStore`（内置镜像并发探测 /healthz + 用户自定义实例优先）解决，本次不动。

## 数据源选型（实测结论）

| 候选 | 实测结果 | 结论 |
| --- | --- | --- |
| `https://rsshub.app/api/routes` | 403（Cloudflare 拦截） | 否决 |
| `https://rsshub.rssforever.com/api/routes` | 503（同实例 `/healthz` 200，说明是接口本身不可用） | 否决 |
| `https://rss.injahow.cn/api/routes` | 404 | 否决 |
| `https://rsshub.app/radar-rules.js` | 403；镜像站返回首页 HTML | 否决 |
| **`https://docs.rsshub.app/routes.json`** | **200，8.4MB JSON** | **采用** |

文档站这份 JSON 是 RSSHub 文档构建的产物，结构与源码路由定义同步，字段比 `/api/routes` 全得多：命名空间（name / url / categories / heat）、路由（path / name / description / categories / heat / parameters / example / topFeeds / radar / features）。其中 `heat`（订阅热度）与 `topFeeds`（实例上真实被订阅的 feed，带标题）是做「发现」的关键，实例侧 API 不提供。

原始 8.4MB 不能直接塞进 APK，也不能每次冷启下载。精简（丢 maintainers / location / features / radar / test / view，截断描述与参数说明，示例最多 3 条）后 **1.08MB**，gzip 后约 0.28MB。

精简逻辑有两份实现（python 生成内置快照 / Kotlin 处理在线更新），实测一致性：1979 命名空间、3800 路由，结构与关键字段零差异，仅 13 条路由的描述字段有字符级差别（两个正则引擎压平 markdown 表格的边界行为不同）。

## Decision

**内置快照 + 在线更新，两份数据同 schema（slim schema）。**

- 随包 `app/src/main/assets/rsshub-routes.json`，由 `scripts/build-route-catalog.py` 从文档站生成，随发版更新；首次安装离线即可浏览全量目录。
- 用户在抽屉或「我的」页点「更新目录」时，`RouteCatalogStore.refresh()` 抓原始 JSON → `RouteCatalogSlimmer` 精简 → 写 `files/rsshub-routes.json`（先写 .tmp 再改名），之后优先读缓存。
- 装载路径只有一条：`RouteCatalogFile` 反序列化 → `toCatalog()`，内置与缓存走同一份代码。
- 全量常驻内存（3800 条），检索用 `RouteCatalogQuery` 线性打分（名字前缀 > 名字包含 > 命名空间 > path > 示例标题，热度作同分排序），结果上限 200 条。不上 Room、不上 FTS。

**path 参数语法由 `RoutePath` 统一处理**：`:key`、`:key?`、`:key{正则}`、`:key{正则}?`，花括号可嵌套（`{[0-9]{2}}`）。三个能力：解析参数列表（决定表单顺序）、`build`（必填缺失返回 null，可选项留空整段删除）、`match`（用示例 path 反填参数，通配参数 `.+` 可吃掉多段）。

**示例订阅（`RouteExample`）**：优先取 `topFeeds` 中未报错的（带标题），最多 3 条；没有则退回官方 `example`。3799 / 3800 条路由有示例。

### slim schema

字段名压到 1–2 字符（key 名是体积大头），三处同步：本表、`RouteCatalogFile.kt`、`scripts/build-route-catalog.py`。

| 字段 | 含义 |
| --- | --- |
| `v` / `generatedAt` | schema 版本 / 生成时刻（epoch millis） |
| `namespaces{n:名称, u:域名, c:分类, h:热度, r:路由[]}` | 命名空间 |
| `r[]{p:path, n:名称, h:热度, c:分类, d:描述, pm:参数说明, po:可选值, pd:默认值, e:示例[]}` | 路由，`r` 按热度降序 |
| `po[]{v:值, l:标签}` / `e[]{p:路径, t:标题}` | 参数选项 / 示例 |

### 兜底

- 缓存解析失败 → 删除缓存，回落内置快照。
- 更新后 `namespaces` 为空 → 视为失败，不覆盖可用数据。
- 93 条路由的 `parameters` 与 path 参数名对不上（RSSHub 自身的文档缺陷）→ 表单参数以 **path 解析结果**为准，说明缺失只是少一行文案。
- 目录装载 / 更新失败只提示，不阻塞手填 URL 订阅。

## Considered Options

- **每次冷启联网拉目录（否决）**：首次使用无网络 = 加订阅功能整体不可用；且每次打开抽屉都要等网络。
- **Room + FTS5 存路由（否决）**：3800 条 × 1.1MB 的数据，一次全量扫描是个位毫秒级；引入建表、迁移、FTS 分词（中文还要额外配 tokenizer）换不来可感知的收益。
- **只保留精选 N 条（否决）**：等于继续替用户决定「哪些源值得订阅」，回到 14 条的老问题。
- **用 RSSHub Radar 的 radar-rules.js（否决）**：那份数据是为「当前网页匹配哪些路由」设计的（URL 匹配规则），不含热度与示例，也不覆盖全部路由；且实测拿不到。

## Consequences

- APK 体积增加约 0.3MB（assets 内压缩后的 1.1MB JSON）。
- 内置快照会随 RSSHub 迭代而过期：靠用户手动更新兜底。不做自动更新（静默下载 8MB 与「用户可预期」的原则冲突，且目录更新不紧急）。
- 新增分类时只需补 `RouteCategory.ORDER` 与 `label()` 的映射；未映射的分类键会原样显示英文，不会崩。
- 检索是线性扫描，`RESULT_LIMIT` 截断是刻意的：宽查询命中上千条时全量渲染没有意义，用户继续输入自然收敛。
- 目录只解决「发现」，不解决「验证」——拼出的 URL 仍要走 `probeFeed` 校验才能订阅，这条链路与手填完全一致。
