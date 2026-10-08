package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import com.cycling.rssradar.core.domain.recommendation.RecommendationSeeds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 冷启动种子：用户手选的兴趣领域（SharedPreferences）。
 *
 * 只存**用户亲手勾的那份选择**，不存算出来的画像——画像每次由真实行为现算
 * （见 `RecommendationScoring.buildProfile`），存一份副本就等于给自己造一个必然会过期的真相源。
 *
 * 读档时过滤掉不在 [RecommendationSeeds.TOPICS] 里的值：词表会变，
 * 而档里留着一个已删除的领域，表现为"用户看不到它、也取消不掉它"。
 */
class RecommendationSeedStore(private val prefs: SharedPreferences) {

    private val _seeds = MutableStateFlow(read())

    /** 当前勾选的领域（已按词表过滤、去重）。 */
    val state: StateFlow<List<String>> = _seeds.asStateFlow()

    /** 勾选 / 取消一个领域。 */
    fun toggle(topic: String) {
        val next = if (topic in _seeds.value) _seeds.value - topic else _seeds.value + topic
        persist(next)
    }

    fun clear() {
        persist(emptyList())
    }

    private fun read(): List<String> = RecommendationSeeds.sanitize(
        (prefs.getString(KEY_SEEDS, null) ?: "").split(SEPARATOR),
    )

    /** 落盘后同步推给订阅者：否则各 ViewModel 只能拿到建 VM 那刻的快照。 */
    private fun persist(next: List<String>) {
        val clean = RecommendationSeeds.sanitize(next)
        prefs.edit { putString(KEY_SEEDS, clean.joinToString(SEPARATOR)) }
        _seeds.value = clean
    }

    private companion object {
        private const val KEY_SEEDS = "recommendation_seeds"
        private const val SEPARATOR = "\u001F"
    }
}
