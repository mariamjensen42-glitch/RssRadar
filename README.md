# RssRadar

以 [RSSHub](https://docs.rsshub.app/) 为核心的 Android RSS 阅读器。订阅源经解析后入库，供信息流浏览、检索与详情阅读。

> 当前为私有仓库，本文档为中文版。

## ✨ 功能特性

### 核心阅读器

- **订阅最小闭环**：添加订阅源 → 解析 → 入库 → 信息流浏览。
- **RSSHub 路由目录**：「添加订阅」抽屉内置路由目录，按分类浏览、搜索热门路由，填参数即订阅；手填普通 RSS/Atom 链接同样支持。
- **解析与正文**：摘要与正文分离取优；feed 自带全文优先，缺失时在打开文章时按需抓取原网页正文（带文件缓存，失败静默回退摘要）。
- **信息流**：全部 / 未读 / 收藏 / 稍后读 四个过滤 tab；下拉刷新全部订阅源；列表分页加载，滚动到底自动续载。
- **文章详情**：收藏、稍后读、查看原文；支持通过 `rssradar://article/{id}` 深链接从外部直接打开文章。
- **订阅源管理**：分组的新建 / 重命名 / 删除，订阅源编辑、删除、跨分组移动（支持多选批量移动），信息流按分组筛选。
- **订阅源文章列表**：在订阅源清单里点击任一源，进入该源全部文章（新→旧，分页），可单独刷新此源。
- **稍后读列表**：独立入口集中查看所有标记为稍后读的文章。
- **深色模式**：浅色 / 深色 / 跟随系统三档，阅读页同步适配。
- **订阅源类型标识**：RSSHub 构建的源与常规 RSS/Atom 源在清单中一眼区分。
- **站点图标**：自动抓取源站点图标，仅首次获取、写入后不覆盖，加载失败回落字母占位。

### AI 与阅读体验

- **AI 摘要**：详情页常驻摘要卡片，一键生成全文概括；生成后持久保存，刷新订阅源不覆盖。
- **AI 翻译**：外文文章一键整体译为简体中文（替换式显示），随时切回原文；使用需自行配置 DeepSeek API Key。
- **阅读排版设置**：详情页内调整字号、行距、左右边距与字体族（系统 / 衬线 / 等宽），即改即见、全局记忆。
- **正文渲染**：原生渲染器（Compose）为主，解析无结果时自动回退 WebView；公式、表格、图片占位均有专门处理。
- **OPML 导入导出**：支持标准 OPML 订阅清单，文件夹结构还原为分组；导入「盲导」不联网逐个校验，文章由导入后刷新补齐。
- **RSSHub 实例设置**：查看与切换 RSSHub 实例地址（内置 rsshub.app / rsshub.rssforever.com 等预设），路由生成使用自定义实例。

### 性能与维护

- **分页列表**：所有列表分页加载（每页 30 条），适配上千订阅源、数万篇文章，内存占用大幅下降。
- **并行刷新**：全量刷新多路并行（并发上限 8），总耗时大幅缩短。
- **自动同步**：WorkManager 按设定周期后台刷新；完成后按归档策略清理到期文章（收藏 / 稍后读永不清理）。
- **归档**：文章到期真删（非标记隐藏），保留期限可配，默认永久。
- **全文抓取诊断**：「我的 → 正文抓取」可查看每次抓取的成败与耗时统计，便于排查个别站点抓取失败。

## 📥 安装步骤

### 方式一：下载 APK（推荐）

1. 打开仓库的 [Releases](../../releases) 页面，下载最新签名 APK（`app-release.apk`）。
2. 在手机上打开 APK，首次安装时按系统提示允许「安装未知来源应用」。
3. 安装完成即用，无需注册账号，所有数据仅存本机。

### 方式二：本地构建

见 [🔨 构建](#-构建) 一节。

## 📖 使用示例

### 订阅一个 RSSHub 路由

以「知乎热榜」为例：

1. 底部抽屉点 **FAB → 添加订阅**，进入内置的 **RSSHub 路由目录**。
2. 搜索「知乎热榜」或按分类浏览，选中路由。
3. 按路由要求填参数（如知乎热榜无必填参数，直接下一步），确认预览后点 **订阅**。
4. 回到信息流，下拉刷新即可拉到第一批文章。

订阅成功后，Feed URL 形如 `https://rsshub.app/zhihu/hotlist`（实例地址取决于你在「我的」里选择的 RSSHub 实例）。

### 订阅普通 RSS / Atom 源

在添加订阅抽屉直接粘贴 feed 地址，例如：

- `https://blog.example.com/feed.xml`（WordPress 等常规博客）
- `https://www.ruanyifeng.com/blog/atom.xml`

### 导入 OPML

「我的 → OPML 导入」选择标准 OPML 文件。导入是「盲导」：不联网逐个校验，文件夹结构还原为分组，导入后刷新一次订阅源即可补齐文章。

### 启用 AI 摘要 / 翻译

1. 「我的」页填入你自己的 **DeepSeek API Key**（仅存本机，不上传）。
2. 打开任意文章详情，点摘要卡片生成 **AI 摘要**；外文文章点翻译按钮一键转中文。

### 外部直达文章

通过深链接 `rssradar://article/{id}` 可从外部（如 Tasker、快捷指令）直接打开某篇文章的详情页。

## 🛠 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose（BOM 2026.02.01）+ Material 3 1.4.0 |
| 架构 | MVI + 类型安全导航（Navigation Compose 2.10.0）+ Hilt 2.60.1 依赖注入 |
| 本地持久化 | Room 2.8.4 |
| 网络 | OkHttp 4.12.0、Coil 3.3.0（图片） |
| 解析 | Rome 2.1.0（RSS/Atom）、Jsoup 1.18.3、readability4j 1.0.8（按需抓正文） |
| 后台 | WorkManager 2.10.1（自动同步） |
| 图标 | lucide compose-icons 2.2.1（线性描边风格） |
| 序列化 | kotlinx-serialization-json 1.7.3 |

## 📋 运行要求

- Android 12（API 31）及以上设备安装 / 调试。
- 编译环境：`compileSdk` 37、`minSdk` 31、`targetSdk` 37；JDK 17+（AGP 运行要求）；Gradle（项目自带 wrapper）。

## 🔨 构建

```bash
git clone <repo-url> RssRadar
cd RssRadar

# 调试包
./gradlew assembleDebug

# 发布包（需在环境变量提供签名：RSSRADAR_KEYSTORE_PATH / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD）
./gradlew assembleRelease
```

发布包通过 GitHub Actions 自动构建：仓库打 `v*` tag 即触发 Release workflow，生成签名 APK（Draft Release）。本地无签名环境变量时自动缺省，日常构建不受影响。

推送 / PR 均会触发 CI 跑单元测试，`dev` 与 `main` 双分支均受保护。

## 🔑 AI 功能配置

AI 摘要 / 翻译调用 DeepSeek 大模型。使用前在「我的」页填入你自己的 DeepSeek API Key，密钥仅保存在本机，不会上传。

## 📁 项目结构

```
app/                   # Android 应用模块（Compose UI、ViewModel、数据层）
  src/main/kotlin/.../data/        # 数据库（Room）、解析、网络、Store
  src/main/kotlin/.../ui/          # 各屏幕可组合项与主题
  src/main/kotlin/.../di/          # Hilt 模块
docs/                 # 领域文档（ADR 决策记录、agent 指南）
scripts/              # 工具脚本（如 RSSHub 路由目录快照生成）
prototype/            # 早期 UI 原型与静态校验脚本
```

更多设计决策见 `docs/adr/`（如正文/摘要分离、导航与 Hilt、MVI 契约、OPML 盲导、分页快照、WebView 混合渲染、原生渲染器、WorkManager 后台同步、RSSHub 路由发现）。

## 🤝 贡献指南

### 分支模型

仓库采用 **dev / main 双分支**模型：

- **`dev`**：日常开发分支。小改动直接 commit + push 到 `dev`，不需要开 PR。
- **`main`**：稳定发布分支。改动在 `dev` 上攒批后，通过 PR `dev → main` 合入；在 `main` 上打 `v*` tag 触发自动发版。
- **feature / fix 分支**：仅高风险或大改动时使用，基于 `dev` 拉出，完成后走 PR 回 `dev`。

### 提交规范

- Commit message 使用祈使句，简明描述改动内容。
- 关联 Issue 时在 message 中写 `Closes #n`（合并到 `main` 时自动关闭对应 Issue）。
- 涉及不可逆设计决策（数据库 schema、架构选型、外部协议等）须先在 `docs/adr/` 写 ADR，再动代码。

### 开发与验证

- 提交前请确保单元测试通过（CI 会在 push / PR 时自动跑）。
- 新增 Room 字段时注意同步列表查询的轻量列清单（见 `data/` 下 `ARTICLE_LIST_COLUMNS`）。
- UI 相关改动建议先跑 `prototype/check-symbols.py` 做图标 / import / 主题色的静态交叉校验。

### Issue

- Bug 报告请附：复现步骤、预期行为、实际行为、设备与系统版本。
- 功能建议请说明使用场景与预期效果。

## 📄 许可证

本项目以 MIT 许可证开源，详见 [LICENSE](LICENSE)。
