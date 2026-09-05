package com.cycling.rssradar.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 导航路由：类型安全的目的地标识（ADR-0002）。
 *
 * 4 个主屏（Feed / Subscriptions / Search / Me）为顶层 composable 目的地；
 * [ArticleDetailRoute] 为独立 composable 目的地（带 articleId）；
 * [FeedArticlesRoute] 为订阅源文章列表（从订阅源清单进入，单源浏览）；
 * [FeedActionRoute] 为 composable 目的地，内部用 Material3
 *   ModalBottomSheet 渲染（注：navigation-compose 2.10 已移除 nav 的 bottomSheet DSL，
 *   故采用 composable + ModalBottomSheet 的等价实现，行为一致：进 back 栈、预测性返回）。
 * Group 对话框与加订阅抽屉都是纯弹层，不入路由（加订阅抽屉由
 * MainActivity 的局部布尔状态控制显隐，曾用 nav 目的地承载，已废弃）。
 *
 * NavHost 与各目的地的装配见后续提交。
 */
@Serializable data object FeedRoute

@Serializable data object SubscriptionsRoute

@Serializable data object SearchRoute

@Serializable data object MeRoute

@Serializable data class ArticleDetailRoute(val articleId: Long)

/** 订阅源文章列表（CONTEXT.md「Feed article list」）：单源浏览，单列表不分 tab。 */
@Serializable data class FeedArticlesRoute(val feedId: Long)

@Serializable data class FeedActionRoute(val feedId: Long)

/** 全文抓取诊断（ADR-0012）：抓不到/抓不全的记录清单与按站点归因。 */
@Serializable data object FetchDiagnosticsRoute

/** 兴趣画像（ADR-0013）：推荐流的画像只读展示，回答"为什么推荐这些"。 */
@Serializable data object InterestProfileRoute

/** 崩溃日志（issue #61）：最近 5 次崩溃的清单与全文导出。 */
@Serializable data object CrashLogRoute

// —— 设置二级页（主页只留分组入口，见 RssHubSettingsScreen） ——

/** 通用：外观、列表显示、推荐、链接与分享。 */
@Serializable data object SettingsGeneralRoute

/** 同步与清理：自动同步、文章清理、新文章通知。 */
@Serializable data object SettingsSyncRoute

/** RSSHub：实例、自定义实例、内置镜像、路由目录。 */
@Serializable data object SettingsRssHubRoute

/** AI 与诊断：DeepSeek Key、AI 功能开关、用量、全文抓取诊断、崩溃日志。 */
@Serializable data object SettingsAiDiagRoute

/** AI 功能总览（AI 智能功能模块）：35 项独立开关、用量看板、任务队列、预算设置。 */
@Serializable data object AiFeaturesRoute

/** 提示词模板管理（AiFeature.PROMPT_TEMPLATE）：内置模板预览 + 各订阅源摘要提示词覆盖的集中管理。 */
@Serializable data object PromptTemplatesRoute

/**
 * AI 产物中心（AI 智能功能模块）：按功能摊开 `ai_artifacts` 里的全部产物。
 *
 * 它是 35 项功能的**通用出口**——只有一部分功能有专属展示位，
 * 其余功能的产物至少要有地方能看见，否则"跑成功了但看不到结果"无从自查。
 */
@Serializable data class AiArtifactsRoute(
    /** 打开时预选的功能（dbValue）；null = 全部。总览页「查看结果」按功能直达。 */
    val featureDbValue: Int? = null,
)
