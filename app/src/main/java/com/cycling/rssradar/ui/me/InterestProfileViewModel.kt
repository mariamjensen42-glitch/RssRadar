package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.core.domain.recommendation.ProfileTerm
import com.cycling.rssradar.data.Recommendation
import com.cycling.rssradar.data.db.FeedEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 一个订阅源的亲和度（诊断页展示行）。 */
data class FeedAffinityRow(
    val feedId: Long,
    val title: String,
    /** [0,1]，1 = 打开率最高的那个源。 */
    val affinity: Double,
)

data class InterestProfileUiState(
    val loading: Boolean = true,
    /** 兴趣词（top 200 里取前若干个展示）。 */
    val terms: List<ProfileTerm> = emptyList(),
    /** 源亲和度，按高到低。 */
    val affinities: List<FeedAffinityRow> = emptyList(),
) {
    /** 冷启动：还没学过任何偏好，推荐走退化排序。 */
    val isColdStart: Boolean get() = !loading && terms.isEmpty() && affinities.isEmpty()
}

/**
 * 兴趣画像页（ADR-0013 可解释性）：把推荐流"为什么推这些"摊开给用户看。
 * 数据全部来自本机真实行为——没有画像就是没有，不编造兴趣类别。
 */
@HiltViewModel
class InterestProfileViewModel @Inject constructor(
    private val recommendation: Recommendation,
    private val repository: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InterestProfileUiState())
    val state: StateFlow<InterestProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val profile = recommendation.profile()
            val feeds: List<FeedEntity> = repository.observeFeeds().first()
            val titles = feeds.associate { it.id to it.title }
            _state.value = InterestProfileUiState(
                loading = false,
                terms = profile.terms,
                affinities = profile.feedAffinity.entries
                    .map { (feedId, affinity) ->
                        FeedAffinityRow(
                            feedId = feedId,
                            title = titles[feedId].orEmpty(),
                            affinity = affinity,
                        )
                    }
                    .sortedByDescending { it.affinity },
            )
        }
    }
}
