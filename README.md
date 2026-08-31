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
- **订阅源管理**：分组的新建 / 重命名 / 删除，订阅源编辑、删除、跨分组移动，信息流按分组筛选。
- **订阅源文章列表**：在订阅源清单里点击任一源，进入该源全部文章（新→旧，分页），可单独刷新此源。
- **稍后读列表**：独立入口集中查看所有标记为稍后读的文章。
- **深色模式**：浅色 / 深色 / 跟随系统三档，阅读页同步适配。
- **订阅源类型标识**：RSSHub 构建的源与常规 RSS/Atom 源在清单中一眼区分。
- **站点图标**：自动抓取源站点图标，仅首次获取、写入后不覆盖，加载失败回落字母占位。

### AI 与阅读体验

- **AI 摘要**：详情页常驻摘要卡片，一键生成全文概括；生成后持久保存，刷新订阅源不覆盖。
- **AI 翻译**：外文文章一键整体译为简体中文（替换式显示），随时切回原文；使用需自行配置 DeepSeek API Key。
- **阅读排版设置**：详情页内调整字号、行距、左右边距与字体族（系统 / 衬线 / 等宽），即改即见、全局记忆。
- **OPML 导入导出**：支持标准 OPML 订阅清单，文件夹结构还原为分组；导入「盲导」不联网逐个校验，文章由导入后刷新补齐。
- **RSSHub 实例设置**：查看与切换 RSSHub 实例地址，路由生成使用自定义实例。

### 性能与维护

- **分页列表**：所有列表改为分页加载（每页 30 条），适配上千订阅源、数万篇文章，内存占用大幅下降。
- **并行刷新**：全量刷新多路并行，总耗时大幅缩短。
- **自动同步**：WorkManager 按设定周期后台刷新；完成后按归档策略清理到期文章（收藏 / 稍后读永不清理）。
- **归档**：文章到期真删（非标记隐藏），保留期限可配，默认永久。

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

- Android 7.1（API 31）及以上设备安装 / 调试。
- 编译环境：`compileSdk` 37、`minSdk` 31、`targetSdk` 37；JDK 11+；Gradle（项目自带 wrapper）。

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

## 🔑 AI 功能配置

AI 摘要 / 翻译调用 DeepSeek 大模型。使用前在「我的」页填入你自己的 DeepSeek API Key，密钥仅保存在本机，不会上传。

## 📁 项目结构

```
app/                   # Android 应用模块（Compose UI、ViewModel、数据层）
  src/main/kotlin/.../data/        # 数据库（Room）、解析、网络、Store
  src/main/kotlin/.../ui/          # 各屏幕可组合项与主题
  src/main/kotlin/.../di/          # Hilt 模块
docs/                 # 领域文档（ADR 决策记录、agent 指南）
prototype/            # 早期 UI 原型与静态校验脚本
```

更多设计决策见 `docs/adr/`（如正文/摘要分离、导航与 Hilt、MVI 契约、OPML 盲导、分页快照、WebView 混合渲染、WorkManager 后台同步）。

## 🤝 贡献

欢迎 Issue 与 PR。提交信息建议使用 `Closes #n` 关联对应 GitHub Issue（会自动关闭）。

## 📄 许可证

本项目以 MIT 许可证开源，详见 [LICENSE](LICENSE)。
