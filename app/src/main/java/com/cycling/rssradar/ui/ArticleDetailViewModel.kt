package com.cycling.rssradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cycling.rssradar.data.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArticleDetailViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _article = MutableStateFlow<ArticleWithFeed?>(null)
    val article: StateFlow<ArticleWithFeed?> = _article.asStateFlow()

    fun load(articleId: Long) {
        viewModelScope.launch {
            _article.value = repository.getArticle(articleId)
            if (_article.value?.article?.isRead == false) {
                repository.markRead(articleId)
            }
        }
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

    companion object {
        fun factory(container: com.cycling.rssradar.AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ArticleDetailViewModel(container.repository) }
            }
    }
}
