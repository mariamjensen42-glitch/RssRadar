package com.cycling.rssradar.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.domain.recommendation.ProfileTerm
import com.cycling.rssradar.core.data.recommend.Recommendation
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.store.prefs.RecommendationSeedStore
import com.cycling.rssradar.core.domain.recommendation.RecommendationSeeds
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
    /** 可勾选的领域（[RecommendationSeeds.TOPICS]）。 */
    val topics: List<String> = RecommendationSeeds.TOPICS,
    /** 用户已勾选的领域。 */
    val seeds: Set<String> = emptySet(),
) {
    /** 冷启动：还没学到任何偏好，推荐走退化排序。 */
    val isColdStart: Boolean get() = !loading && terms.isEmpty() && affinities.isEmpty()
}

/**
 * 兴趣画像页：把推荐流"为什么推这些"摊开给用户看。
 * 数据全部来自本机真实行为——没有画像就是没有，不编造兴趣类别。
 *
 * 唯一的可写项是**冷启动种子**：用户手选的领域，权重弱于真实阅读（见 [RecommendationSeeds]）。
 */
@HiltViewModel
class InterestProfileViewModel @Inject constructor(
    private val recommendation: Recommendation,
    private val repository: FeedRepository,
    private val seedStore: RecommendationSeedStore,
) : ViewModel() {

    private val _state = MutableStateFlow(InterestProfileUiState())
    val state: StateFlow<InterestProfileUiState> = _state.asStateFlow()

    init {
        load()
        // 种子是页面的一部分（诊断页要显示它），所以订阅而不是读一次快照：
        // 用户勾完当场就能在下面的词袋里看到它。
        viewModelScope.launch {
            seedStore.state.collect { seeds ->
                _state.value = _state.value.copy(seeds = seeds.toSet())
            }
        }
    }

    /** 勾选 / 取消一个领域。改完立刻重算画像——用户应当当场看到它进了词袋。 */
    fun toggleTopic(topic: String) {
        seedStore.toggle(topic)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val profile = recommendation.profile(seeds = seedStore.state.value)
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
