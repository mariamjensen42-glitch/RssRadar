package com.cycling.rssradar.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.FeedRepository
import com.cycling.rssradar.playback.PlaybackController
import com.cycling.rssradar.playback.PlaybackTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 播放页的状态：把自己打开的那一篇所在的**同源音频队列**交给播放器，并定位到它。
 *
 * 队列按源组织（"听完这集接着听同一节目的下一集"），所以在页内就能上一集/下一集。
 * 状态本身来自 [PlaybackController]——播放器活在服务里，本页只是个观察者；
 * 退出页面再进来不会打断播放，也不会重新起播。
 */
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val repository: FeedRepository,
    val playback: PlaybackController,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<PlaybackTrack>>(emptyList())
    val queue: StateFlow<List<PlaybackTrack>> = _queue.asStateFlow()

    private val _missing = MutableStateFlow(false)

    /** 这篇没有音频地址（或文章已不在库里）：页面要如实说，别转圈转到天荒地老。 */
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    private var openedFor: Long? = null

    fun open(articleId: Long) {
        if (openedFor == articleId) return
        openedFor = articleId
        viewModelScope.launch {
            val item = repository.getArticle(articleId)
            val article = item?.article
            val url = article?.mediaUrl
            if (article == null || url.isNullOrBlank()) {
                _missing.value = true
                return@launch
            }
            _missing.value = false
            val tracks = repository.audioQueueOfFeed(article.feedId).mapNotNull { sibling ->
                val siblingUrl = sibling.mediaUrl
                if (siblingUrl.isNullOrBlank()) {
                    null
                } else {
                    PlaybackTrack(
                        articleId = sibling.id,
                        url = siblingUrl,
                        title = sibling.title,
                        feedTitle = item.feedTitle,
                        artworkUrl = sibling.coverUrl,
                    )
                }
            }
            _queue.value = tracks
            playback.playQueue(tracks, tracks.indexOfFirst { it.articleId == articleId }.coerceAtLeast(0))
        }
    }
}
