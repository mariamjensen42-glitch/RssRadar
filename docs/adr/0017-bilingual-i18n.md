# ADR-0017 界面中英双语（i18n 架构）

- 状态：已接受
- 日期：2026-09-08
- 相关：ADR-0015（正文获取可见性——错误文案本地化的主要来源）、
  `docs/readyou-feature-comparison.md`

## 背景

软件要面向外国用户可用：界面需支持中英双语切换，覆盖所有功能模块与提示信息。
现状盘点（2026-09-08 实测）：

- `values/strings.xml` 只有 4 行（app 名 + intent 动作名），**零资源化**；
- **130 个 Kotlin 文件、约 6279 行**含硬编码中文（UI 文案、错误标签、原因解释）；
- 无 `values-en/`，无语言切换设置。

所以这不是「补一份英文翻译」，是把全部文案资源化的工程，必须分批。

## 决策

### 1. 机制：Android 标准资源 + 应用内语言切换器

- 中文文案进 `values/strings.xml`（默认），英文进 `values-en/strings.xml`；
  代码侧一律 `stringResource(R.string.x)`。
- 「跟随系统」为默认：系统语言是英文时英文资源自动生效。
- 通用设置页（General）提供显式切换：**跟随系统 / 中文 / English**。
  - API 33+：`LocaleManager.setApplicationLocales`（系统级 per-app locale，
    Manifest 声明 `localeConfig`，系统自动重建界面，stringResource / 日期格式 /
    WebView Accept-Language 全部跟随）。
  - API 31/32：无系统级 per-app locale，`MainActivity.attachBaseContext` 用
    `createConfigurationContext` 覆盖（`app/i18n/AppLocales`），切换后手动
    `recreate()`。
- 语言偏好的单一真相源：`SettingsPrefs` 的 `app_language` 键，
  `LanguageStore`（core/data/store，与 ThemeStore 同构）持久化 + StateFlow 广播。

### 2. 本地化范围

| 范围 | 是否做 | 说明 |
|---|---|---|
| UI 文案（菜单/按钮/表单/Tab/对话框/空状态） | 做 | 主体工作量 |
| 错误提示与失败原因（FetchFailure.label 等） | 做 | 枚举 + UI 层映射，见 §3 |
| 通知文案 | 做 | 跟随其模块批次 |
| AI prompt 结构 | **不做** | 中文 prompt 效果稳定；输出语言跟随界面语言（prompt 内指示模型用当前语言回答） |
| 动态数据（feed 名、文章内容、RSSHub 路由名） | **不做** | 外部数据，不属于本地化 |
| 帮助文档/Onboarding | 做完主体后评估 | 有页面才谈翻译 |

### 3. 非 Compose 语境的错误文案：枚举 + UI 层映射

ViewModel / Engine 层（纯 JVM 测试覆盖）**禁止持有 android 资源**：
错误信息存枚举/密封类，UI 层负责映射到 `stringResource`。
沿用现有 `FetchFailure.label` 模式，把 `label` 从字符串属性改为资源引用
（或 UI 层 when 映射），理由与 ADR-0015 相同。

### 4. 落地节奏

按模块分批（每批一个 commit 到 dev，`check-kotlin.py` 守门）：

1. **批1（本 ADR 附带）**：基础设施——LanguageStore + AppLocales + 设置页语言切换
   + strings 约定。语言设置页自身双语，作为示范。
2. 批2 起：按模块资源化迁移（article 17 文件 > me 15 > feed 6 > subscriptions 4 >
   sync 4 > 其余），错误文案枚举化与迁移同批走。
3. **每批交付物**：代码 + 中英 strings + 「切英文后需真机冒烟的页面清单」
   （英文比中文长 30%~100%，Text 溢出只有真机能暴露）。

英文文案由 AI 出稿，用户抽查术语（如 摘要→Summary、自愈→Self-heal、墓碑→Tombstone）。

### 5. 明确的坑（给后续批次的备忘）

- `stringResource` 拿不到非 Compose 语境；Toast/Snackbar 文案在 Composable 层组装。
- 复数与格式化用 `<plurals>` / `%1$s` 占位符，禁止字符串拼接翻译。
- 中文测试名里的反引号规则不受影响；测试断言若比对 UI 文案，改比对枚举。
- 资源名前缀按模块（`feed_*`、`article_*`、`settings_*`…），防止 6k+ 文案后重名失控。

## 后果

- 界面语言切换即时生效，外国用户系统语言为英文即默认英文（资源迁移完成后）。
- 迁移期中英文资源同时存在，**资源结构不完整时英文回退中文**（资源解析的固有行为），
  不会崩。
- 工作量集中且线性：每个文件只是把字面量换成资源引用，无架构风险。
