package com.cycling.rssradar.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 导航路由：类型安全的目的地标识（ADR-0002）。
 *
 * 4 个主屏（Feed / Subscriptions / Search / Me）为顶层 composable 目的地；
 * [ArticleDetailRoute] 为独立 composable 目的地（带 articleId）；
 * [AddSubscriptionRoute] 为嵌套 nav graph（两步流 Catalog→Params，VM navGraph 作用域共享）；
 * [FeedActionRoute] 为 composable 目的地，内部用 Material3
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

/**
 * 加订阅两步流的嵌套 nav graph（ADR-0002 / issue #33）。
 *
 * [AddSubscriptionRoute] 作为 graph route（承载 navGraph 作用域的 AddSubscriptionViewModel），
 * 两个目的地 [AddSubscriptionCatalogRoute]（目录）→ [AddSubscriptionParamsRoute]（填参）
 * 跨步骤共享同一 VM 实例；graph 整体出栈时 VM 随之销毁，状态天然清空。
 */
@Serializable data object AddSubscriptionRoute

@Serializable data object AddSubscriptionCatalogRoute

@Serializable data object AddSubscriptionParamsRoute

@Serializable data class FeedActionRoute(val feedId: Long)
