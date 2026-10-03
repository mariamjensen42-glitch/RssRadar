package com.cycling.rssradar.ui.annotations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.annotation.AnnotationRepository
import com.cycling.rssradar.core.data.db.AnnotationWithArticle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 标注列表页：全库高亮与笔记，按标注时间倒序。
 *
 * 删除走**二次确认**：标注是读者自己划出来的东西，误删没有撤销入口
 * （DB 里删就是删），比多一次点击代价大得多。
 */
@HiltViewModel
class AnnotationsViewModel @Inject constructor(
    private val annotationRepository: AnnotationRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<AnnotationWithArticle>>(emptyList())
    val items: StateFlow<List<AnnotationWithArticle>> = _items.asStateFlow()

    private val _pendingDelete = MutableStateFlow<AnnotationWithArticle?>(null)
    val pendingDelete: StateFlow<AnnotationWithArticle?> = _pendingDelete.asStateFlow()

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
