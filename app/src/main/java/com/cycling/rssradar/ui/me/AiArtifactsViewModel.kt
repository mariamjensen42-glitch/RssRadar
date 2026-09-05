package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.ai.AiArtifactGroup
import com.cycling.rssradar.core.data.ai.AiArtifactItem
import com.cycling.rssradar.core.data.ai.AiArtifactRepository
import com.cycling.rssradar.core.data.ai.AiPayloadLine
import com.cycling.rssradar.core.data.ai.AiPayloadText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


/** 产物中心的一条产物及其渲染结果。解析放在打开时做，列表页不预先算。 */
data class AiArtifactDetail(
    val item: AiArtifactItem,
    /** 结构化 JSON 摊平后的可读行。 */
    val lines: List<AiPayloadLine>,
    /** 格式化后的模型原文。 */
    val raw: String,
)


data class AiArtifactsUiState(
    val loading: Boolean = true,
    /** 按功能聚合的概览，筛选用的功能条由它得出。 */
    val groups: List<AiArtifactGroup> = emptyList(),
    val items: List<AiArtifactItem> = emptyList(),
    /** 选中的功能；null = 全部功能。 */
    val selectedKind: Int? = null,
    val detail: AiArtifactDetail? = null,
    val message: String? = null,
)


/** 产物中心的意图（ADR-0003 MVI 契约）。 */
sealed interface AiArtifactsIntent {
    data class SelectKind(val kind: Int?) : AiArtifactsIntent
    data class OpenDetail(val item: AiArtifactItem) : AiArtifactsIntent
    data object DismissDetail : AiArtifactsIntent
    data class Delete(val item: AiArtifactItem) : AiArtifactsIntent
    data object Refresh : AiArtifactsIntent
    data object ConsumeMessage : AiArtifactsIntent
}


/**
 * AI 产物中心的状态宿主。
 *
 * 这个页面存在的理由值得写下来：35 项 AI 功能里，只有一部分有专属展示位。
 * 其余功能执行器照常跑、产物照常落 `ai_artifacts`，但用户在 App 里找不到任何
 * 地方能看到它们——表现为"跑成功了，结果呢？"。产物中心不挑功能、不认识
 * payload 的具体类型，把所有产物按功能摊开，让每一项功能至少有一个能看见结果的地方。
 *
 * 两个口径上的取舍：
 * 1. **只列真的产出过的功能**。把 35 项全列出来、其中一半是空的，
 *    用户只会得出"一半功能是坏的"这个错误结论。没产出的功能该去看开关与任务队列。
 * 2. **读取失败不抛**。这是诊断页，查询挂了也要给出原因，而不是闪退或白屏。
 */
@HiltViewModel
class AiArtifactsViewModel @Inject constructor(
    private val artifacts: AiArtifactRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AiArtifactsUiState())
    val state: StateFlow<AiArtifactsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onIntent(intent: AiArtifactsIntent) {
        when (intent) {
            is AiArtifactsIntent.SelectKind -> {
                if (_state.value.selectedKind == intent.kind) return
                _state.update { it.copy(selectedKind = intent.kind) }
                refresh()
            }

            is AiArtifactsIntent.OpenDetail -> openDetail(intent.item)
            AiArtifactsIntent.DismissDetail -> _state.update { it.copy(detail = null) }

            is AiArtifactsIntent.Delete -> delete(intent.item)
            AiArtifactsIntent.Refresh -> refresh()
            AiArtifactsIntent.ConsumeMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val kind = _state.value.selectedKind
            // 概览与列表分开查：概览要全部功能的计数，列表受当前筛选影响。
            val groups = quiet(emptyList()) { artifacts.overview() }
            val items = quiet(emptyList()) { artifacts.browse(kind = kind) }
            _state.update { it.copy(loading = false, groups = groups, items = items) }
        }
    }

    /**
     * 查询失败时回落默认值，但**不吞协程取消**。
     *
     * 直接写 `runCatching { ... }.getOrDefault(...)` 会把 [CancellationException]
     * 一起吃掉——Room 的挂起查询是可取消的，页面退出时协程被取消，
     * 取消信号被吞掉意味着这次取消"没发生"，破坏结构化并发。
     * 这是诊断页，查询出错要给出空列表而不是崩，但取消必须照常往上传。
     */
    private suspend fun <T> quiet(default: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            default
        }

    /**
     * 打开详情：先把 payload 渲染成可读行再进 state。
     *
     * 渲染放 IO 线程而不是 Composable 里——payload 最长可达数 KB，
     * 在主线程解析 JSON 会在列表滑动这种最需要流畅的时刻带来掉帧。
     */
    private fun openDetail(item: AiArtifactItem) {
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                AiArtifactDetail(
                    item = item,
                    lines = AiPayloadText.lines(item.payload),
                    raw = AiPayloadText.prettyRaw(item.payload),
                )
            }
            _state.update { it.copy(detail = rendered) }
        }
    }

    private fun delete(item: AiArtifactItem) {
        viewModelScope.launch {
            val ok = quiet(false) {
                artifacts.delete(item.feature, item.subjectId)
                true
            }
            if (!ok) _state.update { it.copy(message = "删除失败，请重试") }
            // 删掉的正是当前打开的这条时，面板要一起关，否则会停在一个已不存在的数据上。
            val wasOpen = _state.value.detail?.item === item
            if (wasOpen) _state.update { it.copy(detail = null) }
            refresh()
        }
    }
}
