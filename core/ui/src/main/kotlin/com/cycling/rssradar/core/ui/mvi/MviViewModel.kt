package com.cycling.rssradar.core.ui.mvi

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MVI 事件面契约（候选 A，ADR-0003）：只有单一 onIntent 入口，不含状态。
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

/** 一次性副作用（弹 Snackbar、导航、请求权限），不该留在界面状态里。 */
interface MviEffect

/** 放基类而非各 VM 自建 SharedFlow，让「谁有副作用通道」在类型上可见。 */
class MviEffectChannel<E : MviEffect> {
    private val _effects = MutableSharedFlow<E>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val effects: SharedFlow<E> = _effects.asSharedFlow()

    /** onIntent 是同步函数发不了 emit，裸 tryEmit 无缓冲会静默丢光消息，缓冲深度取 1。 */
    fun tryEmit(effect: E) {
        _effects.tryEmit(effect)
    }
}
