package com.cycling.rssradar.ui.me

import android.content.Context
import androidx.lifecycle.ViewModel
import com.cycling.rssradar.core.data.maintenance.CrashLog
import com.cycling.rssradar.core.data.maintenance.CrashRecord
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 崩溃日志页状态：清单 + 正在查看的详情。 */
data class CrashLogUiState(
    val records: List<CrashRecord> = emptyList(),
    val detail: CrashDetail? = null,
)

/** 崩溃日志页事件（候选 A）。 */
sealed interface CrashLogIntent {
    data object Refresh : CrashLogIntent
    data class OpenDetail(val record: CrashRecord) : CrashLogIntent
    data object CloseDetail : CrashLogIntent
    data object Clear : CrashLogIntent
}

@HiltViewModel
class CrashLogViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CrashLogUiState())
    val uiState: StateFlow<CrashLogUiState> = _uiState.asStateFlow()

    /**
     * 落盘/读取都在 IO：崩溃日志可能有几十 KB，别在主线程啃文件。
     *
     * Context 由 Composable 侧在发 Intent 时传入，不进状态也不存字段——
     * ViewModel 持 Context 会活得比界面长，配置变更后拿到的是旧 Activity。
     */
    fun onIntent(intent: CrashLogIntent, context: Context) {
        when (intent) {
            CrashLogIntent.Refresh -> viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = _uiState.value.copy(records = CrashLog.list(context))
            }
            is CrashLogIntent.OpenDetail -> viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = _uiState.value.copy(
                    detail = CrashDetail(intent.record.name, intent.record.head, CrashLog.read(context, intent.record.name))
                )
            }
            CrashLogIntent.CloseDetail -> _uiState.value = _uiState.value.copy(detail = null)
            CrashLogIntent.Clear -> viewModelScope.launch(Dispatchers.IO) {
                CrashLog.clear(context)
                _uiState.value = CrashLogUiState()
            }
        }
    }
}
