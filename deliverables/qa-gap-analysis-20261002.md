# RssRadar 质量保证（QA）覆盖差距分析

日期：2026-10-02
范围：静态代码分析、单元测试、集成测试、功能与 UI 测试、性能测试、安全测试
结论依据：仓库内实测（CI 运行日志、lint MR 报告、构建产物、源码），非推测

---

## 结论摘要

对六类测试流程逐条比对后，RssRadar 真正缺失的只有**两类半**：

| 类别 | 判定 | 关键事实 |
|---|---|---|
| 静态代码分析 | 部分覆盖 | `lintVital` 随 release 自动跑且报告干净；全量 lint 此前不在 CI 中，现已接入 |
| 单元测试 | **强项** | JUnit 4，71 个测试文件 / 实测运行 626 用例；缺口是 `core:ui` 无测试源集、无覆盖率 |
| 集成测试 | 部分覆盖 | JVM 层集成路径已覆盖；无 HTTP 协议层（MockWebServer）、无契约测试 |
| 功能与 UI 测试 | 缺失 | `androidTest` 仅模板文件；替代策略是把渲染逻辑抽成纯 JVM 函数 |
| 性能测试 | 部分覆盖 | 测量手册与真实基线完备；但全人工，无自动化、无 CI 门禁 |
| 安全测试 | 缺失 | 无 SAST / SCA / 密钥扫描；现已接入 CodeQL + Dependabot + secret scanning |

一句话判断：**单元测试远超平均水平，不需要追加投资；短板集中在静态分析未进门禁、安全扫描为零、覆盖率无数据。**

---

## 一、静态代码分析

### 现状

- **AGP 的 `lintVitalRelease` 实际已在 release 构建中执行**。产物位于
  `app/build/intermediates/lint_vital_intermediate_text_report/release/lintVitalReportRelease/lint-results-release.txt`，
  内容为 `No issues found.`
  这一点容易被误判为「完全没有静态分析」。
- `abortOnError` / `warningsAsErrors` / `baseline` 均未配置，无强制门禁。
- 全仓库无 detekt、ktlint、spotless，也无 `.editorconfig`。
- **工具链层面唯一的替代品是自研检查脚本**，且其能力是通用工具覆盖不到的领域约束：
  - `scripts/check-kotlin.py`：编译诊断 + R 桩生成 + error/warning 分离（带 `.built` 缓存）
  - `scripts/check-room-schema.py`：Room schema 与 migration 的一致性比对
  - `scripts/check-ai-ui-wiring.py`：AI 功能 UI 接线检查

### 缺口

`lintVital` 只关心 fatal 级别，不是全量 lint；且**不参与 CI**，报告不归档、问题不可追踪。

### 已实施（见第七节）

`lintDebug` 已接入 CI，按模块矩阵运行，报告归档 + SARIF 进代码扫描。

---

## 二、单元测试

### 现状

- JUnit 4（`junit 4.13.2`）。
- 测试文件 **71 个**，`@Test` 字面量 650 处，**实测运行 626 用例**（差额来自注释与模板残留）。
- 按模块分布：`core:data` 277、`app` 190、`core:domain` 172、`core:model` 11。
- 主源码 227 个 `.kt` —— 测试密度处于较高水平。
- 测试栈极简：无 MockK、无 Truth、无 Robolectric、无 MockWebServer，假实现全部手写
  （`FakeSharedPreferences` 等）。这是为「绕开 gradle 直接以 JUnitCore 驱动」付出的代价。
- 自研 `scripts/run-tests.py` 承担调度，含两项防假绿机制：
  - 从**源文件** package 声明推导测试 FQCN，产物缺失时显式告警，避免跑陈旧 `.class` 得到无关的「全绿」
  - `pick_newest()` 去重同一 artifact 的多版本 jar，规避 `NoSuchMethodError` 类假故障

### 缺口

1. **`core/ui` 只有 `main` 源集，没有 `test` 源集**，该模块零测试。
2. **无覆盖率工具**（无 jacoco / kover），覆盖率无任何数据，也无门禁。

---

## 三、集成测试

### 现状

集成路径在 JVM 层已有覆盖，代表性用例：

- 抓取链路：`RefreshEngineConditionalTest`（304 条件请求）、`RefreshEngineHealTest`（自愈）、`RefreshEngineProgressTest`
- 内容获取：`ContentFetcherTest`、`ArticleExtractorTest`、`OnDemandFetchTest`
- 协议解析：`RssParserTest`、`OpmlParserTest`、`OpmlWriterTest`
- 序列化与备份：`BackupSerializationTest`
- 更新检查：`UpdateCheckerTest`

### 缺口

- 无 `MockWebServer`，**HTTP 协议层未测**（状态码、重试、304、超时、体积上限）。
- 无契约测试；RSSHub 依赖靠运维脚本 `scripts/probe-rsshub-instances.py` 人工探测。
- 无真机集成测试。

---

## 四、功能与 UI 测试

### 现状

- `app/src/androidTest/` 下**仅有一个模板生成的 `ExampleInstrumentedTest.kt`**。
- `androidx.compose.ui:ui-test-junit4` 与 `espresso-core` 依赖已声明，但**零用例**。
- CI 无 `connectedAndroidTest`（需要真机或模拟器）。
- **替代策略值得肯定**：把渲染逻辑抽成纯 JVM 可测函数，例如
  `ReadingNodesTest`、`ReadingImagesTest`、`ReadingContentHtmlTest`、`MathMlTest`、
  `ArticleImageDecodeTest`、`ReadingThemeColorsTest`。在「禁止本地 gradle + 无真机 CI」的约束下，这是成本收益比最高的做法。

### 缺口

端到端回归测试为零；无截图对比测试；发布前依赖人工真机验收。

---

## 五、性能测试

### 现状

测量方法学完备，文档质量高：

- `docs/performance-measurement.md`：adb 命令口径、合格线（冷启动 1500 ms / Janky frames 5% / PSS 250 MB）、
  四条实测陷阱（debug 包数字不可用、需 `svc power stayon`、深链会吹大 3 倍、后台同步污染采样窗口）、
  记录模板。
- `docs/perf/compose-performance.md`。
- 真实基线（Redmi Note 10 Pro / Android 13 / release 包，中位数）：
  冷启动 **286 ms**、滚动 Janky **0.81%**、点击打开文章 **5.8%**（p95 = 13 ms）、
  122 KB 长文内部滚动 0.29%、PSS **145 MB**。
- `EXPLAIN QUERY PLAN` 作为索引是否生效的唯一直接验证手段已写入文档。

### 缺口

**全部依赖人工执行**：

- 无 `macrobenchmark` 模块（`settings.gradle.kts` 只有 `app` 与 4 个 `core` 模块）。
- 无 CI 性能回归门禁；发版前是否测完全靠自觉。
- 无自产 baseline profile —— 构建产物里的 `baseline-prof.txt` 是库 AAR 合并来的，非本项目产出。

---

## 六、安全测试

### 现状

本类此前**覆盖为零**，是六项中最大的空白：

- 无 SAST（`lintVital` 的 fatal 级别不足以充当）。
- 无 SCA / 依赖漏洞扫描，无 Dependabot 配置。
- 无密钥扫描。
- 无 DAST。
- 需注意区分：`release.yml` 中的「验签 APK + 计算 SHA-256」属于**发布完整性校验**，不是安全测试，不可混算。

### 已实施（见第七节）

CodeQL 代码扫描、Dependabot 告警与安全修复、GitHub 原生 secret scanning 与推送保护。

---

## 七、已落地实施：GitHub Actions 侧

实施原则：**能力尽量压在 GitHub 原生设施上，不引入新的构建插件或第三方平台。**

### 7.1 新增/修改的文件

| 文件 | 作用 |
|---|---|
| `.github/dependabot.yml` | 新增。gradle 版本目录 + github-actions 每周检查 |
| `.github/workflows/ci.yml` | 新增 `lint` job（模块矩阵） |
| `.github/workflows/codeql.yml` | 新增。CodeQL 代码扫描 |

未改动 `release.yml` 与 `pages.yml`，未改动任何 `build.gradle.kts`，未引入 jacoco / kover / detekt / ktlint。

### 7.2 依赖扫描（Dependabot）

两个 ecosystem，均为每周一 21:00（Asia/Shanghai），PR 落 `dev` 分支：

- `gradle`：按互斥分组收敛为 3 组 —— `androidx` / `kotlin`（含 ksp）/ `rest`
- `github-actions`：自动提升 action 版本

开启后依赖侧当前告警数为 **0**。

### 7.3 静态分析门禁（Android Lint）

设计要点：

- **按模块矩阵**运行（`app` / `core:data` / `core:ui`），`fail-fast: false`。
  这一点是必要的：单 job 串行时，`core:data` 失败会**中止整个构建**，导致 `:app:lintDebug` 根本没有机会运行。
- **门禁强度按事件区分**：`continue-on-error: ${{ github.event_name != 'pull_request' }}`
  —— 推送 `dev` 时只产出报告不拦（避免日常推送被存量问题刷红），
  **PR → `main`（发布闸门）时硬拦**。
- 报告以 glob `lint-results-debug.*` 归档（AGP 实际产出 html / sarif / txt / xml）。
- SARIF 上传至代码扫描，`category: lint-<模块>`，结果进入仓库 Security 标签页。
  该步骤独立 `continue-on-error`，报告上传失败不干扰门禁。

### 7.4 安全扫描

- **CodeQL**（`.github/workflows/codeql.yml`）：语言 `java-kotlin`，`build-mode: manual`
  （Kotlin 必须构建，`none` 模式无法分析 Kotlin），构建步骤为 `./gradlew :app:assembleDebug --no-daemon`。
  触发：PR → `main`、push `main`、每周日 21:00 兜底、手动。**不在 dev 推送时触发**，避免高频长任务。
- **仓库安全特性**（已开启）：
  - `secret_scanning` ✅
  - `secret_scanning_push_protection` ✅
  - Dependabot 告警 ✅ 与自动安全修复 ✅
  - `secret_scanning_validity_checks` 与 `non_provider_patterns` 未开启 —— 需 GitHub Secret Protection（付费），当前不在免费范围。

---

## 八、CI 实测发现

首次全量 lint 跑出的问题清单与**逐条误报判定**。

### 8.1 `MissingPermission`（1 处，判定：误报）

```
core/data/src/main/kotlin/com/cycling/rssradar/core/data/notify/NotificationHelper.kt:85
Error: Call requires permission which may be rejected by user
```

`postNewArticles()` 的实际写法是安全的：

1. 入口处 `if (!hasPermission(context)) return false`，而 `hasPermission()` 在 API ≥ 33 时
   确实调用了 `ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS)`；
2. `notify()` 外层包了 `runCatching`，`SecurityException` 会被捕获，不会崩溃。

Lint 的 `MissingPermission` 检查只识别**直连 `if` 守卫**与 **`try/catch`**，
无法看穿「helper 函数封装检查」与 **`runCatching`** 这两种写法，因而误报。

附带发现（非崩溃，属契约不一致）：`runCatching` 吞掉异常后函数仍 `return true`，
而 KDoc 声明「无权限时静默跳过（返回 false）」。异常路径下返回值不真实，量级极小。

### 8.2 `LocalContextGetResourceValueCall`（10 处 / 9 个位置，判定：全部误报）

| 文件 | 行 |
|---|---|
| `app/.../ui/feed/FeedListScreen.kt` | 183、184、199、200 |
| `app/.../ui/search/SearchScreen.kt` | 107、108 |
| `app/.../ui/article/ReaderImagePage.kt` | 189、205、213 |

逐处核实结论：**全部位于 `LaunchedEffect` 或 `scope.launch` 块内**，
这些块**不在组合作用域**，无法使用 `stringResource`，只能经 `context.getString` 取文案
—— 这正是 ADR-0017 定下的写法，`SearchScreen.kt:100` 甚至留有显式说明注释。

Lint 的该条检查无法区分「组合作用域内的 `LocalContext` 误用（真问题）」与
「effect / 协程内的唯一可行取法（必然如此）」，在 i18n 资源化之后必然集中触发。

### 8.3 其他 warning（不影响门禁）

| 规则 | 数量 |
|---|---|
| `UnusedResources` | 45 |
| `GradleDependency` | 11 |
| `PluralsCandidate` | 10 |
| `AutoboxingStateCreation` | 10 |
| `NewerVersionAvailable` | 9 |
| `ConfigurationScreenWidthHeight` | 6 |
| `TypographyDashes` | 6 |
| `ModifierParameter` / `UseKtx` | 各 3 |

合计 `app` 模块 103 warnings / 10 hints。`GradleDependency` 与 `NewerVersionAvailable` 交由 Dependabot 消化；
`UnusedResources` 45 条与 i18n 资源化过程中的废弃条目相关，值得单独清理一轮。

### 8.4 处置建议

这 11 个 error 全部是误报，且成因是**结构性、系统性的**（不是个别写法瑕疵），
逐处加注解会产生 11 个 `@SuppressLint` 噪声。建议采用 **lint baseline**：

```
./gradlew :app:updateLintBaseline :core:data:updateLintBaseline
```

在 Android Studio 中执行后提交生成的 `lint-baseline.xml`，并在模块 `android { lint { baseline = ... } }` 中引用。
baseline 的作用是**冻结存量、只卡新增**，正好匹配「既有 11 条经判定可接受」的现状，
且不牺牲对将来新引入的真实问题的拦截能力。

**在该 baseline 落地之前，PR → `main` 的 lint 门禁会处于失败状态**（push dev 不受影响）。

---

## 九、优先级与未做项

### 已做（本轮）

1. Dependabot（SCA / CVE）—— 两个 ecosystem，PR 落 dev
2. Android Lint 进入 CI，模块矩阵 + 按事件分级门禁 + 报告归档 + SARIF 进代码扫描
3. CodeQL（SAST），`java-kotlin` 手动构建模式
4. GitHub 原生 secret scanning 与推送保护、Dependabot 告警与自动安全修复

### 待办

| 优先级 | 事项 | 说明 |
|---|---|---|
| P0 | lint baseline 落地 | 消除 11 条误报对发布闸门的阻塞，见 8.4 |
| P0 | 清理 45 条 `UnusedResources` | 与 i18n 批次资源化的废弃条目相关 |
| P1 | 覆盖率（kover / jacoco） | 只需在 CI 跑；当前覆盖率完全无数据 |
| P1 | 补 `core/ui` 测试源集 | 该模块目前零测试 |
| P2 | secret scanning validity checks | 需付费的 GitHub Secret Protection，或改用 CI 内 gitleaks |
| P3 | macrobenchmark 自动化 | 需真机 runner，CI 成本高；建议先把现有手册固化为发版 checklist |
| P3 | Espresso / Compose UI 测试 | 性价比低于现有「抽纯函数」策略，暂不引入 |
| — | 明确不做 | 第三方同步、TTS、小部件、Bionic Reading；Postman / JMeter / k6 / Playwright 等面向 Web 与服务端的工具不适用本客户端项目 |

---

## 十、实施过程记录（可复用要点）

发布于 CI 配置过程中的四个实测结论，均属「不看日志就会踩」的类型：

1. **Gradle 项目任务路径用冒号，不用斜杠**。
   `:core/ui:lintDebug` 会报
   `Cannot locate tasks that match ':core/ui:lintDebug' as project 'core/ui' not found`；
   正确写法是 `:core:ui:lintDebug`。文件系统路径与 Gradle 任务路径必须分开传递。

2. **`github/codeql-action/upload-sarif` 不接受 `**/` 前导 glob**，
   会把值当字面路径并报 `Path does not exist`。应传精确模块路径或目录。
   而 `actions/upload-artifact` 接受 `**/` glob，两者行为不一致。

3. **Android Lint 的产物格式随 lint 选项而变**。
   无问题时只产出 html + sarif；有问题时额外产出 txt + xml。
   归档路径用 `lint-results-debug.*` 通配最稳，写死 `.xml` 会漏文件、写死 `.html` 会漏 SARIF。

4. **`concurrency.cancel-in-progress` 会掐掉未完成的 job**。
   该项目 dev 分支存在高频连续推送，lint 这类长任务常被后续推送取消，
   表现为 `unit-tests` 成功而 `lint` 为 `cancelled` —— 这不代表 lint 有问题，
   但会让「验证 lint 配置」变得困难，需等推送间歇或改用手动触发。
