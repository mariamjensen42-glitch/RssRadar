package com.cycling.rssradar.ui.annotations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.annotation.AnnotationRepository
import com.cycling.rssradar.core.data.db.AnnotationWithArticle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 标注列表页：全库高亮与笔记，按标注时间倒序。
 *
 * 删除走**二次确认**：标注是读者自己划出来的东西，误删没有撤销入口
 * （DB 里删就是删），比多一次点击代价大得多。
 */
/**
 * 标注列表页的渲染输入快照，唯一产出点是 [AnnotationsViewModel.uiState]。
 */
data class AnnotationsUiState(
    val items: List<AnnotationWithArticle> = emptyList(),
    val pendingDelete: AnnotationWithArticle? = null,
)

@HiltViewModel
class AnnotationsViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<AnnotationWithArticle>>(emptyList())
    val items: StateFlow<List<AnnotationWithArticle>> = _items.asStateFlow()

    private val _pendingDelete = MutableStateFlow<AnnotationWithArticle?>(null)
    val pendingDelete: StateFlow<AnnotationWithArticle?> = _pendingDelete.asStateFlow()

    /** 列表与待确认删除的两份状态合成一份，UI 只订阅这一条。 */
    val uiState: StateFlow<AnnotationsUiState> = combine(items, pendingDelete) { items, pending ->
        AnnotationsUiState(items = items, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnnotationsUiState())

    init {
        viewModelScope.launch {
            annotationRepository.observeAllWithArticle().collect { _items.value = it }
        }
    }

    fun askDelete(item: AnnotationWithArticle) {
        _pendingDelete.value = item
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val item = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { annotationRepository.delete(item.annotation.id) }
    }
}
