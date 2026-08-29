package com.cycling.rssradar.ui.mvi

/**
 * MVI 事件面契约（候选 A，ADR-0003）。
 *
 * 仅定义单一 `onIntent` 入口，不含 `uiState`：碎片状态 VM（Subscriptions / ArticleDetail /
 * FeedList）当前尚未聚合单一 `UiState`（候选 C 才做），若接口含 state 会提前侵入 C。
 * 候选 C 落地后升级为 `MviViewModel<I, S>`。不为 lifecycle 引入抽象基类
 *（`@HiltViewModel` 已接管构造）。
 */
interface MviViewModel<I> {
    fun onIntent(intent: I)
}
