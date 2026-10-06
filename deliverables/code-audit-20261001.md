# RssRadar 全量代码审计与缺陷修复

- 日期：2026-10-01
- 分支：dev @ `fb6a4d1`（前序 `a67f98f`）
- 方法：6 路并行深挖（解析抓取 / AI / DB / UI / 基建 / 测试）+ **逐条人工核实**
- 范围：`app/src/main`（66 文件）+ `core/*`（89 文件）+ `scripts/` + 资源与 CI

## 结论速览

| 项 | 结果 |
|---|---|
| 提交修复 | **8 项**（含 3 个 P0） |
| 拦下的误报 | **4 项**（子代理报告里约 1/4 不成立，未核实就改会引入新 bug） |
| 验证 | `check-kotlin.py` 0 error · `run-tests.py` **508 tests OK** · CI unit-tests 通过 |
| 最重要发现 | **测试数字长期虚高**：旧口径 686 中有约 178 个跑在已删除/改包的幽灵类上 |

---

## 一、已修复（8 项）

### P0-1 AI 付费调用绕过预算闸（`core/data/ai/AiRepository.kt`）

`generateSummary()` 与 `translate()` 直接调 `client.chat(...)`，**完全绕开**
`AiRateLimiter`——既无日预算闸、也无最小间隔闸，还不经功能开关、不进记账。
后果：额度用尽后这两个入口仍能无限调用 DeepSeek，用量页也看不到这些消耗。

修复：抽出私有 `chatGuarded()` 把「预算 + 最小间隔 + 记账」收口到一处（与
`AiFeatureRunner.execute` 同源）；新增 `SummaryOutcome.OutOfBudget` /
`TranslationOutcome.OutOfBudget`，VM 补分支；`AppModule` 装配补 limiter + featureStore。

> 讽刺的是 `AiRateLimiter` 的类注释正好写着「必须收在一处，否则手动点两下就能绕过预算」——
> 该隐患在同项目内真实发生了。

### P0-2 DeepSeek API Key 明文进入云备份（安全）

`AiStore` 把 Key 存在与全部设置共用的 `rssradar_settings`，而
`backup_rules.xml` / `data_extraction_rules.xml` 仍是**未修改的模板**（`allowBackup=true`）
→ Key 随 Google 云备份上传到用户账号，旧机型还能被 `adb backup` 整份导出。

修复：新增 `SettingsPrefs.NAME_SECRETS`（`rssradar_secrets`），装配点
`AiStore.migrateFromLegacy()` 搬迁旧值并**抹掉旧位置**，两个备份规则文件排除该文件
（云备份 + 设备间直传都排除）。其余设置照常备份。

### P0-3 工具链假绿（测试可信度）

三个独立缺陷，共同导致「测试全绿」可能与当前代码无关：

| 缺陷 | 修复 |
|---|---|
| `run-tests.py` 只匹配 `^  app.*error`，core 模块编译失败被放行，测试跑旧字节码 | 改用 check-kotlin 退出码 |
| `check-kotlin.py` 把 warning 一起判失败，与上条契约冲突 | 只有 error 才非零；顺手删掉一行被覆盖的死代码 |
| `test_classes()` 扫 `out/**/*Test.class`，源码改名/删除后旧产物仍被跑 | 改为从**源文件**（package 声明）推导 FQCN 并校验产物存在 |

**实测证据**：`out` 里 91 个 `*Test.class`，源文件只有 **58** 个。其中
`CalendarDayLabelTest` / `DayGroupsTest` / `MarkAsReadTest` / `ReadingImageStoreTest` /
`ScrollSlotsTest` / `RouteCatalogQueryTest` / `MathSanitizeProbeTest` 的源已不存在。
旧口径的 **686 里有约 178 个跑在幽灵类上**，真实测试数是 **508**。

### P1-4 切篇后旧文章的 AI 结果覆盖新文章（`ArticleDetailViewModel.kt`）

`runAi()` 起的协程不挂在 `loadJob` 之下，切篇不会取消它——慢半拍回来的
AI 产物 / 报错 / 全文提取会写到**新文章**上（表现为「AI 摘要是上一篇的」）。

修复：结果落 UI 前判 `currentArticleId == articleId`。产物**照常落库**（钱已花），
只拦界面写入；`applyExtractedContent()` 同样加守卫，避免把已切走的正文顶回屏幕。

### P1-5 视图模式不持久化（`ListDisplayStore.kt`）

`update()` 写了 `KEY_VIEW_MODE`，`readPersisted()` 却**从没读它** → 每次重启
列表视图都退回「卡片」，用户选的「杂志/网格」丢失。

### P1-6 英文界面计数仍显「万/亿」（i18n）

`AiFeaturesScreen` / `AiArtifactsScreen` 各有一份 `formatCountEn`，
**定义了但零调用**——调用点全用中文版 `formatCount`。

修复：收敛到 `i18n/CountText.kt`。`formatCount(value)` 按
`LocalConfiguration.locales[0].language` 分流，并固定 `Locale.US`——否则系统语言为
德语/法语时默认 locale 会把小数渲染成「1,2万」。

### P1-7 统计页拼接翻译 + 硬编码单位（i18n）

`ReadingStatsScreen` 的 `"源集中度 $pct%——$…"` 是 ADR-0017 明令禁止的**拼接翻译**；
`formatMinutes` 把 `h`/`m` 硬编码进字符串；`joinToString("、")` 用中文顿号。
已资源化为 `stats_concentration` / `duration_hours(_minutes)` / `list_separator`。

### P1-8 设置页 3 处未资源化（i18n）

`SettingsSubPages` 的 `"${count} 条"`、`"已配置" / "未配置"` → 资源化。

---

## 二、核实后判定为误报（未改）

| 报告项 | 核实结论 |
|---|---|
| UI「切篇不重置滚动位置」 | `LaunchedEffect(articleId)` 里已有 `scrollState.scrollTo(0)` |
| DB「dedupeKey 缺唯一索引」 | 全库**没有** `dedupeKey` 字段 |
| DB「observeFeedCount 用错 DAO」 | 用途是空态补偿，`COUNT(*) FROM articles` 语义正确 |
| DB「ORDER BY 无 tiebreaker」 | 主列表已带 `publishedAt DESC, fetchedAt DESC` |

---

## 三、未修清单（按优先级，需产品决策或属重构）

### 值得尽快处理

1. **AI 冷却误伤**：对话中报错后 `nextAllowedAt` 长冷却，从用户视角等同「坏了」，
   且提示未说明何时恢复。
2. **AI 消耗成本未落库**：算出了费用却没存，用量页看不到花了多少。
3. **AI 输入无长度闸门**：超长正文截断策略不完整，可能撞 token 上限或产生高额调用。
4. **AI 产物读取无异常保护**：`AiArtifactRepository` 反序列化未包 `try/catch`，
   脏 JSON 会崩 app。
5. **release 资源裁剪**：`shrinkResources` 可能裁掉通知图标（有硬证据），
   需加 keep 规则或验证。
6. **`check-ai-ui-wiring.py` 拼错脚本名**：检查形同虚设，等于没有这道门禁。
7. **自愈候选校验窗口**：全量刷新下自愈几乎不触发（与设计意图不符）。
8. **`AddSubscription` 静默成功**：部分失败路径不告知用户（违反「失败原因必须可见」）。
9. **`FeedList` 刷新/加载更多竞态**：并发触发可能重复分页。
10. **`update()` 非原子**：多字段设置项可能读到半更新状态。

### 明确的优化项（非缺陷）

11. 三处 `ORDER BY COALESCE(publishedAt, fetchedAt) DESC`（推荐候选 / 相关候选 / 画像样本）
    是表达式 → 索引失效 + 全表外排序。改成 `publishedAt IS NULL, publishedAt DESC,
    fetchedAt DESC` 可走索引，但**排序语义会变**（无 `publishedAt` 的文章由最前变最后），
    是产品决策不是 bug 修复。
12. `ArticleDetailViewModel` 6 处硬编码中文（批6 白名单）；
    `ArticleContextMenu`(11) / `OpenUrl`(5) 属 i18n 批5 未做。
13. 质量审计遗留（Dimens/Shapes token、拆 `FeedListScreen` 1872 行、组件复用）
    —— 均为重构，非缺陷。
14. 测试覆盖缺口：feed 相关（`FeedListViewModel` 分页）无测试；
    解析/抽正文的启发式分支覆盖薄；probe 测试绑真实网络（flaky 隐患）。

---

## 四、复核命令

```bash
python scripts/check-kotlin.py            # 0 error · 4 warning
python scripts/run-tests.py --no-build    # 508 tests OK
```

> 注意：`run-tests.py` 的 `--no-build` 只跑既有产物；判断真实测试数要看
> 从**源文件**推导的结果，不要数 `out` 目录里的 `.class`（幽灵类会虚高数字）。
