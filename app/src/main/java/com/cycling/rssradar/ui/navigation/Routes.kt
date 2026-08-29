package com.cycling.rssradar.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 导航路由：类型安全的目的地标识（ADR-0002）。
 *
 * 4 个主屏（Feed / Subscriptions / Search / Me）为顶层 composable 目的地；
 * [ArticleDetailRoute] 为独立 composable 目的地（带 articleId）；
 * [AddSubscriptionRoute] / [FeedActionRoute] 为 composable 目的地，内部用 Material3
 *   ModalBottomSheet 渲染（注：navigation-compose 2.10 已移除 nav 的 bottomSheet DSL，
 *   故采用 composable + ModalBottomSheet 的等价实现，行为一致：进 back 栈、预测性返回）。
 * Group 对话框保留 AlertDialog，不入路由。
 *
 * NavHost 与各目的地的装配见后续提交。
 */
@Serializable data object FeedRoute

@Serializable data object SubscriptionsRoute

@Serializable data object SearchRoute

@Serializable data object MeRoute

@Serializable data class ArticleDetailRoute(val articleId: Long)

@Serializable data object AddSubscriptionRoute

@Serializable data class FeedActionRoute(val feedId: Long)
