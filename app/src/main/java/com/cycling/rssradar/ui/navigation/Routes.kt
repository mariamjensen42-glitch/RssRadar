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
