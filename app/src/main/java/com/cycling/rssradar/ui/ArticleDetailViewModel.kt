package com.cycling.rssradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.ArticleEntity
import com.cycling.rssradar.data.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val repository: FeedRepository,
) : ViewModel() {

    private val _article = MutableStateFlow<ArticleWithFeed?>(null)
    val article: StateFlow<ArticleWithFeed?> = _article.asStateFlow()

    /** 正在按需抓取原网页正文。失败是常态（反爬/JS 页），静默降级，UI 不弹错误。 */
    private val _isFetchingContent = MutableStateFlow(false)
    val isFetchingContent: StateFlow<Boolean> = _isFetchingContent.asStateFlow()

    fun load(articleId: Long) {
        viewModelScope.launch {
            _article.value = repository.getArticle(articleId)
            if (_article.value?.article?.isRead == false) {
                repository.markRead(articleId)
            }
            fetchFullContentIfNeeded(articleId)
        }
    }

    /**
     * 文章没有 feed 自带正文时按需抓原网页（ADR-0001）。
     * 成功后重新加载文章；失败静默，详情页继续显示摘要。
     */
    private suspend fun fetchFullContentIfNeeded(articleId: Long) {
        val current = _article.value ?: return
        if (current.article.contentSource != ArticleEntity.CONTENT_SOURCE_NONE) return
        _isFetchingContent.value = true
        runCatching { repository.fetchFullContent(articleId) }
        _isFetchingContent.value = false
        _article.value = repository.getArticle(articleId)
    }

    fun toggleStarred() {
        val current = _article.value?.article ?: return
        viewModelScope.launch {
            repository.setStarred(current.id, !current.isStarred)
            _article.value = _article.value?.copy(
                article = current.copy(isStarred = !current.isStarred),
            )
        }
    }

    fun toggleBookmarked() {
        val current = _article.value?.article ?: return
        viewModelScope.launch {
            repository.setBookmarked(current.id, !current.isBookmarked)
            _article.value = _article.value?.copy(
                article = current.copy(isBookmarked = !current.isBookmarked),
            )
        }
    }
}
