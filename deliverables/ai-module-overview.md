# AI 智能功能模块 — 交付说明

35 项功能全部落地为可运行代码，覆盖数据层、执行层、调度层与 UI。触发策略按确认的
「按需为主 + 后台限速」执行：交互类实时调用，批处理类走 WorkManager 每日任务，
受日预算、并发上限与最小请求间隔三道闸统一约束。

## 一、35 项功能清单

### 内容处理类（15 项）

| # | 功能 | 触发 | 交互入口 | 结果展示 |
|---|------|------|---------|---------|
| 1 | AI 摘要 | 按需 | 阅读页顶栏 Sparkles；订阅源可配专属提示词 | 阅读页标题下常驻摘要卡，持久化 |
| 2 | 文章翻译 | 手动 | 阅读页顶栏翻译开关 | 替换式译文，会话内 LRU 缓存 |
| 3 | 智能分类 | 批处理 | 每日任务；阅读页可重判 | 阅读页话题 chip + 置信度 |
| 4 | 自动标签 | 批处理 | 每日任务；AI 面板按钮 | 阅读页标签 chip 组 |
| 5 | 情感分析 | 批处理 | 每日任务；AI 面板按钮 | 极性 + 强度 + 依据 |
| 6 | 关键词提取 | 批处理 | 每日任务；AI 面板按钮 | 关键词 chip 行 |
| 7 | 观点总结 | 手动 | AI 面板按钮 | 论点列表，区分观点/事实/数据 |
| 8 | 文章问答与深度解析 | 实时 | AI 面板提问框 | 答案 + 引用原句，无依据明说 |
| 9 | 自动提取全文 | 手动 | AI 面板按钮 | 成功替换正文并清不完整标记 |
| 10 | 智能去重 | 批处理 | 每日任务 | 同事件组标识 + 主篇判定 |
| 11 | 文章质量分析 | 批处理 | 每日任务；AI 面板按钮 | 总分 + 四维条 + 短板说明 |
| 12 | 智能降噪与内容评分 | 批处理 | 每日任务；AI 面板按钮 | 价值分 + 噪声信号 + 实质要点 |
| 13 | 长文精读结构化 | 手动 | AI 面板按钮 | 主旨 + 层级大纲（带锚点） |
| 14 | 信源可信度评估 | 手动 | AI 面板按钮 | 档位 + 依据信号 + 存疑点 |
| 15 | 划词解释 | 实时 | AI 面板术语输入 | 贴合上下文的一句话释义 |

### 推荐发现类（10 项）

| # | 功能 | 触发 | 交互入口 | 结果展示 |
|---|------|------|---------|---------|
| 16 | 个性化内容推荐 | 按需 | 信息流「推荐」tab | 推荐流 + 「减少此类」负反馈 |
| 17 | 智能订阅源推荐 | 批处理 | 每日任务 | 建议清单 + 理由，无把握不给 URL |
| 18 | 发现模式 | 按需 | 每日任务预生成 | 跨领域探索流，同来源最多 2 篇 |
| 19 | 话题星系浏览 | 按需 | 订阅页入口 | 话题簇 + 文章数 + 关联强度 |
| 20 | 信息茧房破壁 | 批处理 | 每日任务 | 盲区话题清单 + 破壁文章 |
| 21 | 文章关联推荐 | 按需 | 阅读页底部 | 相关阅读横滑卡片 |
| 22 | 智能信息聚合 | 批处理 | 话题星系「生成综述」 | 共识 / 分歧 / 待观察三段式 |
| 23 | 个人兴趣排序 | 批处理 | 每日任务；兴趣画像页 | 兴趣榜 + 强度条 + 代表源 |
| 24 | 同源事件合并阅读 | 批处理 | 去重产物入口 | 事件时间线 + 各源口径差异 |
| 25 | 兴趣冷启动引导 | 手动 | 推荐 tab 空态引导卡 | 领域勾选 → 画像种子词 |

### 辅助推送类（10 项）

| # | 功能 | 触发 | 交互入口 | 结果展示 |
|---|------|------|---------|---------|
| 26 | AI 每日简报 | 批处理 | 每日任务，通知栏进入 | 要闻列表 + 为什么值得看 + 可跳过（折叠不隐藏） |
| 27 | 生成分享文案 | 手动 | AI 面板按钮 | 短评 / 长推 / 要点体三版 |
| 28 | 智能通知 | 批处理 | 自动同步后判定 | 仅重要文章进通知，被过滤的可查 |
| 29 | 订阅源健康监控 | 批处理 | 每日任务；订阅页入口 | 状态（正常/降频/失效）+ 处置建议 |
| 30 | 阅读习惯分析 | 批处理 | 每日任务 | 活跃时段 + 集中度 + 三条观察 |
| 31 | 每日阅读报告 | 批处理 | 每日任务，次日通知 | 真实数字 + 总结 + 遗漏清单 |
| 32 | 智能过滤规则生成 | 手动 | 设置页入口 | 规则预览（带真实命中示例）确认后启用 |
| 33 | AI 用量看板 | 本地 | 设置页 | 今日/累计调用、字数、失败率 |
| 34 | AI 任务队列 | 本地 | 设置页 | 按状态分组 + 失败原因 + 重试/清空 |
| 35 | 提示词模板管理 | 本地 | 设置页；订阅源操作页 | 全局模板 + 订阅源级覆盖编辑 |

## 二、架构要点

### 数据层（DB v14，三张新表）

- `ai_artifacts` — 所有 AI 产物，(subjectKind, subjectId, kind) 三元组主键。
  **加功能零迁移**：新功能只是新的 kind 值。刻意不加外键（全局产物没有父行），
  孤儿由每日任务清理。
- `feed_ai_profiles` — 订阅源级 AI 配置。三态语义（null = 跟随全局）由
  `FeedAiProfile.resolve` 一处合并。
- `ai_tasks` — 任务队列，dedupeKey 去重 + 退避 + 终态保留 7 天。

### 执行链路

```
AiTaskPlanner（纯函数，决定排什么）
   → AiTaskQueue（入队/领取/重试）
   → AiFeatureRunner（取正文 → 组 prompt → 过限流 → 调模型 → 解析 → 存产物 → 记账）
   → AiBatchProcessor（并发编排，并发度取自预算设置）
   → AiDailyWorker（WorkManager，每日一次，仅非计量网络）
```

手动触发（阅读页按钮）与后台批处理共用同一条执行链路，保证结果一致。

### 防捏造的三道处理

1. Prompt 层：每个模板都写明"只使用给定文本中的信息，没有就说没有"；
   涉及文章 id 的功能要求 id 必须来自给定列表。
2. 解析层：`AiParsers` 兜住代码围栏、废话前缀、字段类型错误、空壳 JSON；
   涉及 id 的载荷额外用 `restrictIds` 按真实候选集过滤。
3. 校验层：`isMeaningful` 判定主字段为空时**不入库并记为失败**，
   避免用户看到一张什么都写不出来的空卡片。

### 成本约束

- 日预算（默认 200 次/天）、并发（默认 2）、最小间隔（默认 1.2 秒）三道闸对所有路径生效。
- 失败的调用同样占额度，否则一个反复失败的任务能烧穿一天额度。
- 额度用尽时任务**推迟一小时**而不是记失败——记失败会触发退避重试，重试又撞额度。
- 用量只统计次数与字数，不换算金额（单价会变，不编造过期数字）。

## 三、改动文件

**新增（core:data）**
`ai/AiFeature.kt`（35 项注册表）、`ai/AiPayloads.kt`、`ai/AiPrompts.kt`、
`ai/AiPromptTexts.kt`、`ai/AiParsers.kt`、`ai/AiRateLimiter.kt`、
`ai/AiArtifactRepository.kt`、`ai/AiTaskPlanner.kt`、`ai/AiTaskQueue.kt`、
`ai/AiFeatureRunner.kt`、`ai/AiBatchProcessor.kt`、`db/AiSchema.kt`、
`store/AiFeatureStore.kt`、`store/AiBudgetStore.kt`

**新增（core:domain）** `ai/AiReadingStats.kt`

**新增（app）**
`ai/AiDailyWorker.kt`、`ui/me/AiFeaturesScreen.kt`、`ui/me/AiFeaturesViewModel.kt`、
`ui/article/AiArticleSheet.kt`

**修改**
`db/AppDatabase.kt`（v14 + 4 个 DAO）、`di/AppModule.kt`（装配 + EntryPoint）、
`ui/navigation/Routes.kt`、`MainActivity.kt`、`ui/me/SettingsSubPages.kt`、
`ui/article/ArticleDetailScreen.kt`、`ui/article/ArticleDetailViewModel.kt`、
`ui/subscriptions/FeedActionScreen.kt`、`ui/subscriptions/SubscriptionsViewModel.kt`、
`CONTEXT.md`

**测试** 新增 4 个测试文件共 34 个用例，全量 604 个用例通过。

## 四、提交前必跑

```bash
python scripts/check-room-schema.py   # Room migration 与 KSP 生成的 schema 一致性
python scripts/check-kotlin.py        # 全量 Kotlin 编译
python scripts/run-tests.py           # 全量单元测试
```

`check-room-schema.py` 是本模块新增的防回归脚本。它拿 Room 的 KSP 产物
`AppDatabase_Impl.kt` 当基准，反向比对源码里手写 migration 的建表与建索引语句，
逐字符报差异（精确到字符码点）。归一化会忽略反引号与括号内侧空格这类纯格式差异，
只报列名 / 类型 / NOT NULL / DEFAULT / 主键 / 索引列这些会影响 `onValidateSchema` 的差异。

**必须跑它的原因**：`check-kotlin.py` 不做 KSP 也不跑 Dagger，
而 Room 的 schema 校验和 Hilt 的绑定校验都只发生在 gradle 阶段。详见「五、已修复的两个坑」。

## 五、已修复的两个坑（都由 gradle 阶段暴露）

### 坑 1 · Hilt MissingBinding

`SubscriptionsViewModel` 直接注入了 `FeedAiProfileDao`，但 `AppModule` 里没有对应的
`@Provides`。`check-kotlin.py` 只做 Kotlin 编译，不跑 Dagger 注解处理器，本地完全无感，
要等 `hiltJavaCompileDebug` 才报。已补 `provideFeedAiProfileDao`。

### 坑 2 · Room schema 不匹配（更致命）

`MIGRATION_13_14` 给 4 个字段写了 SQL `DEFAULT`，但 **Room 不会从 Kotlin 默认值生成
SQL `DEFAULT`，只有 `@ColumnInfo(defaultValue = ...)` 才会**。被坑的字段：

| 字段 | 错误写法 | 正确写法 |
|------|---------|---------|
| `ai_tasks.payload` | `TEXT NOT NULL DEFAULT ''` | `TEXT NOT NULL` |
| `ai_tasks.status` | `INTEGER NOT NULL DEFAULT 0` | `INTEGER NOT NULL` |
| `ai_tasks.attempts` | `INTEGER NOT NULL DEFAULT 0` | `INTEGER NOT NULL` |
| `feed_ai_profiles.updatedAt` | `INTEGER NOT NULL DEFAULT 0` | `INTEGER NOT NULL` |

另外 `ai_tasks.id` 漏了 `NOT NULL`：Room 对自增主键生成的是
`INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`。

这两类错误都会让 `onValidateSchema` 判定不符，**新装用户没事、升级用户一开 App 就崩**——
是最难在开发环境复现的一类问题。

## 六、默认开关策略（重要）

`defaultEnabled` **按触发方式划分**，不是一律保守关闭：

| 触发方式 | 默认值 | 理由 |
|---------|--------|------|
| MANUAL / REALTIME / ON_DEMAND | **开** | 不点就不花钱。默认关等于功能不可达，用户只会以为没做。 |
| BATCH（后台自动跑） | **关** | 会在每日任务里自动消耗额度，属于"要不要花钱"的决策，交用户显式开启。 |

当前 35 项中默认开启 13 项：AI 摘要、文章翻译、观点总结、文章问答、自动提取全文、
长文精读结构化、信源可信度评估、划词解释、生成分享文案、个性化内容推荐、
AI 用量看板、AI 任务队列、提示词模板管理。

## 七、未做 / 后续

- 全局类产物的独立页面：每日简报、阅读报告、习惯分析、健康监控的产物已能生成入库，
  但还没有专属浏览页，目前只存在于 `ai_artifacts` 中。
- 发现模式与话题星系的列表页入口未接（产物与打分逻辑已就绪）。
- 智能通知的过滤结果历史页、过滤规则的启用/编辑页未接。
