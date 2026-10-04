package com.cycling.rssradar.ui.me

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.backup.BackupReader
import com.cycling.rssradar.core.data.backup.BackupWriter
import com.cycling.rssradar.core.data.backup.ConflictPolicy
import com.cycling.rssradar.core.data.backup.ImportReport
import com.cycling.rssradar.core.data.backup.ImportStrategy
import com.cycling.rssradar.core.data.search.SearchIndexer
import com.cycling.rssradar.core.ui.text.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val app: Application,
    private val writer: BackupWriter,
    private val reader: BackupReader,
    private val indexer: SearchIndexer,
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val includeContent: Boolean = true,
        val message: UiText? = null,
        val report: ImportReport? = null,
        val rebuilding: Boolean = false,
    ) {
        val progress: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setIncludeContent(value: Boolean) {
        _state.value = _state.value.copy(includeContent = value)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun export(uri: Uri) {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, done = 0, total = 0, message = null, report = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sink = app.contentResolver.openOutputStream(uri, "wt")
                        ?: error("无法写入所选位置")
                    sink.use {
                        writer.export(
                            sink = it,
                            appVersion = appVersion(),
                            includeContent = _state.value.includeContent,
                            onProgress = ::updateProgress,
                        )
                    }
                }
            }
            _state.value = _state.value.copy(
                running = false,
                done = 0,
                total = 0,
                message = if (result.isSuccess) {
                    UiText.res(R.string.backup_export_done)
                } else {
                    UiText.res(R.string.backup_failed)
                },
            )
        }
    }

    fun import(uri: Uri, strategy: ImportStrategy, conflict: ConflictPolicy) {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, done = 0, total = 0, message = null, report = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val source = app.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选文件")
                    source.use { reader.import(it, strategy, conflict, onProgress = ::updateProgress) }
                }
            }
            val report = result.getOrNull()
            if (report != null) {
                runCatching { indexer.ensureIndexed() }
            }
            _state.value = _state.value.copy(
                running = false,
                done = 0,
                total = 0,
                report = report,
                message = if (result.isSuccess) {
                    UiText.res(R.string.backup_import_done)
                } else {
                    UiText.res(R.string.backup_failed)
                },
            )
        }
    }

    fun rebuildIndex() {
        if (_state.value.rebuilding) return
        _state.value = _state.value.copy(rebuilding = true, done = 0, total = 0, message = null)
        viewModelScope.launch {
            val result = runCatching { indexer.rebuild(onProgress = ::updateProgress) }
            _state.value = _state.value.copy(
                rebuilding = false,
                done = 0,
                total = 0,
                message = if (result.isSuccess) {
                    UiText.res(R.string.backup_rebuild_done)
                } else {
                    UiText.res(R.string.backup_failed)
                },
            )
        }
    }

    private fun updateProgress(done: Int, total: Int) {
        _state.value = _state.value.copy(done = done, total = total)
    }

    private fun appVersion(): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
