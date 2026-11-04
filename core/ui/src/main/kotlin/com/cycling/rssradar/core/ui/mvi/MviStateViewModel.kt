package com.cycling.rssradar.core.ui.mvi

import kotlinx.coroutines.flow.StateFlow

/**
 * MVI 契约：单一 uiState 出口 + 单一 onIntent 入口。
 *
 * 放在 core:ui 而非某个 feature 内：契约被多个 ViewModel 跨包实现，
 * 留在一个 feature 里会让其余 feature 反向依赖它，feature 模块化后即成为环。
 */
interface MviStateViewModel<I, S> {
    /** 状态字段一律叫 uiState：仓库曾并存 state 与 uiState 两种叫法，契约在此收口。 */
    val uiState: StateFlow<S>

    fun onIntent(intent: I)
}
