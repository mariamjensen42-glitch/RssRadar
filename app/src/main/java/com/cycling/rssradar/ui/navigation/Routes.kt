package com.cycling.rssradar.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 导航路由：类型安全的目的地标识（ADR-0002）。
 *
 * 4 个主屏（Feed / Subscriptions / Search / Me）为顶层 composable 目的地；
 * [ArticleDetailRoute] 为独立 composable 目的地（带 articleId）；
 * [AddSubscriptionRoute] / [FeedActionRoute] 为 bottomSheet 目的地；
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
