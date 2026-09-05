package com.cycling.rssradar.ui.me

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.ai.AiTaskScheduler
import com.cycling.rssradar.core.data.ai.AiArtifactRepository
import com.cycling.rssradar.core.data.ai.AiCategory
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiQueueSnapshot
import com.cycling.rssradar.core.data.ai.AiTaskQueue
import com.cycling.rssradar.core.data.store.AiBudgetState
import com.cycling.rssradar.core.data.store.AiBudgetStore
import com.cycling.rssradar.core.data.store.AiFeatureSettings
import com.cycling.rssradar.core.data.store.AiFeatureStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


/** AI 功能总览页的界面状态。 */
data class AiFeaturesUiState(
    val settings: AiFeatureSettings = AiFeatureSettings(),
    val budget: AiBudgetState = AiBudgetState(),
    val queue: AiQueueSnapshot = AiQueueSnapshot(),
    /** 正在跑批处理的标记，避免用户连点「立即执行」。 */
    val running: Boolean = false,
    val message: String? = null,
)


/** AI 功能总览页的意图（ADR-0003 MVI 契约）。 */
sealed interface AiFeaturesIntent {
    data class Toggle(val feature: AiFeature) : AiFeaturesIntent
    data class SetCategory(val category: AiCategory, val enabled: Boolean) : AiFeaturesIntent
    data object ResetDefaults : AiFeaturesIntent
    data object DisableAllPaid : AiFeaturesIntent
    data class SetDailyLimit(val limit: Int) : AiFeaturesIntent
    data class SetConcurrent(val limit: Int) : AiFeaturesIntent
    data class SetMinInterval(val millis: Long) : AiFeaturesIntent
    data object RunNow : AiFeaturesIntent
    data object RetryFailed : AiFeaturesIntent
    data object ClearPending : AiFeaturesIntent
    data object RefreshQueue : AiFeaturesIntent
    data class ClearArtifacts(val feature: AiFeature) : AiFeaturesIntent
    data object ConsumeMessage : AiFeaturesIntent
}


/**
 * AI 功能总览页的状态宿主。
 *
 * 一个必须解释清楚的职责：**开关一变就重新调度后台任务**（[reschedule]）。
 * 若只在页面初始化时调度一次，用户关掉最后一个批处理功能后，
 * 那个每天白跑一次的周期任务会一直留着，直到卸载。
 *
 * 关掉功能时同时清掉它的产物与待执行任务——
 * "关了却还显示 AI 结果"和"关了却还在后台烧额度"都属于用户无从察觉的坑，
 * 必须在开关这一处收口，而不是指望各功能自己处理。
 */
@HiltViewModel
class AiFeaturesViewModel @Inject constructor(
    private val featureStore: AiFeatureStore,
    private val budgetStore: AiBudgetStore,
    private val queue: AiTaskQueue,
    private val artifacts: AiArtifactRepository,
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(AiFeaturesUiState())
    val state: StateFlow<AiFeaturesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            featureStore.state.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            budgetStore.state.collect { budget -> _state.update { it.copy(budget = budget) } }
        }
        refreshQueue()
    }

    fun onIntent(intent: AiFeaturesIntent) {
        when (intent) {
            is AiFeaturesIntent.Toggle -> toggle(intent.feature)
            is AiFeaturesIntent.SetCategory -> setCategory(intent.category, intent.enabled)
            AiFeaturesIntent.ResetDefaults -> {
                featureStore.reset()
                reschedule()
                say("已恢复默认设置")
            }

            AiFeaturesIntent.DisableAllPaid -> {
                featureStore.disableAllPaid()
                reschedule()
                say("已关闭全部会调用模型的功能")
            }

            is AiFeaturesIntent.SetDailyLimit -> budgetStore.setDailyLimit(intent.limit)
            is AiFeaturesIntent.SetConcurrent -> budgetStore.setConcurrentLimit(intent.limit)
            is AiFeaturesIntent.SetMinInterval -> budgetStore.setMinIntervalMs(intent.millis)

            AiFeaturesIntent.RunNow -> runNow()
            AiFeaturesIntent.RetryFailed -> {
                viewModelScope.launch {
                    val n = queue.retryFailed()
                    refreshQueue()
                    say(if (n == 0) "没有失败任务" else "已重新排队 $n 个任务")
                }
            }

            AiFeaturesIntent.ClearPending -> {
                viewModelScope.launch {
                    queue.clearPending()
                    refreshQueue()
                    say("已清空待执行任务")
                }
            }

            AiFeaturesIntent.RefreshQueue -> refreshQueue()

            is AiFeaturesIntent.ClearArtifacts -> {
                viewModelScope.launch {
                    artifacts.clearFeature(intent.feature)
                    say("已清除「${intent.feature.label}」的全部产物")
                }
            }

            AiFeaturesIntent.ConsumeMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun toggle(feature: AiFeature) {
        val next = !featureStore.isEnabled(feature)
        featureStore.set(feature, next)
        if (!next) {
            // 关掉即清场：产物与待执行任务一起清，不留"看得见但关不掉"的残留。
            viewModelScope.launch {
                artifacts.clearFeature(feature)
                queue.dropPendingOf(feature)
                refreshQueue()
            }
        }
        reschedule()
    }

    private fun setCategory(category: AiCategory, enabled: Boolean) {
        featureStore.setCategory(category, enabled)
        if (!enabled) {
            viewModelScope.launch {
                AiFeature.ofCategory(category).forEach {
                    artifacts.clearFeature(it)
                    queue.dropPendingOf(it)
                }
                refreshQueue()
            }
        }
        reschedule()
    }

    private fun runNow() {
        if (_state.value.running) return
        // 没有任何批处理功能开启时，排程会得出 0 个任务——
        // 此时再显示"已在后台开始执行"就是一句空话，用户会对着空气等结果。
        if (AiFeature.BATCH_FEATURES.none { it in featureStore.state.value.enabled }) {
            say("还没有开启任何后台功能，先在下面打开几项再执行")
            return
        }
        _state.update { it.copy(running = true) }
        AiTaskScheduler.runNow(app)
        // 任务是异步跑的，这里只负责把按钮置灰一小会儿，避免连点堆出一串一次性任务。
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            _state.update { it.copy(running = false) }
            refreshQueue()
        }
        say("已在后台开始执行，进度见队列")
    }

    private fun refreshQueue() {
        viewModelScope.launch {
            _state.update { it.copy(queue = queue.snapshot()) }
        }
    }

    private fun reschedule() {
        AiTaskScheduler.reschedule(app, featureStore.state.value.enabled)
    }

    private fun say(message: String) = _state.update { it.copy(message = message) }
}
