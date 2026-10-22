package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.AiCategory
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.model.AiFeatureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 16 项 AI 功能的独立开关。
 *
 * **为什么每个功能一个 key 而不是存一份集合快照**：见 [AiFeature.DEFAULT_ENABLED] 的说明——
 * 集合快照会把升级时刻冻住，新功能永远推不到老用户身上。逐项读 key、缺 key 回落默认值，
 * 新功能才能按其 `defaultEnabled` 自动生效。
 *
 * 关闭一项功能时**同时清掉它的产物**（在 [com.cycling.rssradar.core.data.ai.AiArtifactRepository]
 * 侧执行），不留残留数据——「关了却还在显示 AI 结果」是最难解释的一类 bug。
 */
class AiFeatureStore(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<AiFeatureSettings> = _state.asStateFlow()

    fun isEnabled(feature: AiFeature): Boolean = state.value.isEnabled(feature)

    /** 打开/关闭单项。 */
    fun set(feature: AiFeature, enabled: Boolean) {
        prefs.edit().putBoolean(keyOf(feature), enabled).apply()
        _state.value = AiFeatureSettings(readEnabled())
    }

    fun toggle(feature: AiFeature) = set(feature, !isEnabled(feature))

    /** 分组一键全开/全关：设置页每个分组标题右侧的操作。 */
    fun setCategory(category: AiCategory, enabled: Boolean) {
        val editor = prefs.edit()
        AiFeature.ofCategory(category).forEach { editor.putBoolean(keyOf(it), enabled) }
        editor.apply()
        _state.value = AiFeatureSettings(readEnabled())
    }

    /** 全部恢复出厂默认。 */
    fun reset() {
        val editor = prefs.edit()
        AiFeature.entries.forEach { editor.remove(keyOf(it)) }
        editor.apply()
        _state.value = AiFeatureSettings(readEnabled())
    }

    /** 一键关闭所有会花钱的功能——用量看板里的「紧急刹车」。 */
    fun disableAllPaid() {
        val editor = prefs.edit()
        AiFeature.LLM_FEATURES.forEach { editor.putBoolean(keyOf(it), false) }
        editor.apply()
        _state.value = AiFeatureSettings(readEnabled())
    }

    private fun readEnabled(): Set<AiFeature> =
        AiFeature.entries.filterTo(HashSet()) { prefs.getBoolean(keyOf(it), it.defaultEnabled) }

    private fun readPersisted(): AiFeatureSettings = AiFeatureSettings(readEnabled())

    private fun keyOf(feature: AiFeature): String = "$PREFIX${feature.name.lowercase()}"

    companion object {
        private const val PREFIX = "ai_feature_"

        /** 测试与诊断用：列出所有 key，便于断言「关掉某项确实写了一个 key」。 */
        fun keyFor(feature: AiFeature): String = "$PREFIX${feature.name.lowercase()}"
    }
}
