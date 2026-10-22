package com.cycling.rssradar.core.ui.mvi

import kotlinx.coroutines.flow.StateFlow

/**
 * MVI 事件面契约（候选 A）：只有单一 onIntent 入口，不含状态。
 *
 * 放在 core:ui 而非某个 feature 内：契约被 8 个 ViewModel 跨包实现，
 * 留在一个 feature 里会让其余 feature 反向依赖它，feature 模块化后即成为环。
 */
interface MviViewModel<I> {
    fun onIntent(intent: I)
}

/** MVI 完整契约（候选 C）：单一 uiState 出口 + 单一 onIntent 入口。 */
interface MviStateViewModel<I, S> : MviViewModel<I> {
    /** 状态字段一律叫 uiState：仓库曾并存 state 与 uiState 两种叫法，契约在此收口。 */
    val uiState: StateFlow<S>
}
