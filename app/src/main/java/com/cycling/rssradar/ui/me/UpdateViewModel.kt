package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.update.UpdateCheckResult
import com.cycling.rssradar.core.data.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 检查更新的界面状态。Idle = 还没查过，不占地方也不显示任何文案。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val title: String, val url: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * @param currentVersion 本机版本；拿不到时传空串——此时按「比不出来」处理，
     *   返回 UpToDate 而不是报错（版本拿不到是本机问题，不该甩锅给网络）。
     */
    fun check(currentVersion: String) {
        if (_state.value is UpdateState.Checking) return
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = when (val result = checker.check(currentVersion)) {
                UpdateCheckResult.UpToDate -> UpdateState.UpToDate
                is UpdateCheckResult.Available -> UpdateState.Available(
                    version = result.release.version,
                    title = result.release.title,
                    url = result.release.url,
                )

                is UpdateCheckResult.Failed -> UpdateState.Failed(result.message)
            }
        }
    }
}
