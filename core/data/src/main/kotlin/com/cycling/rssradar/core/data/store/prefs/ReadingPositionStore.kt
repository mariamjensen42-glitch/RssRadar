package com.cycling.rssradar.core.data.store.prefs

import android.content.SharedPreferences
import com.cycling.rssradar.core.model.ReadingPosition

/**
 * 每篇文章的阅读位置（比例），供「重开长文回到上次读到的地方」。
 *
 * 键布局：`reading_pos_<articleId>` 存比例，`reading_pos_order` 存最近写入的 id 顺序（新→旧）。
 * 为什么要 order：位置是**易失的辅助信息**，不该无限增长。超 [LIMIT] 条时按 order 从尾部淘汰，
 * 连同它的比例键一起删——滑出窗口的文章下次打开只是回到开头，不会出现脏数据。
 *
 * 为什么不进 Room：这里没有查询需求（点查 + 淘汰），SharedPreferences 足够；
 * 位置本身随排版变化、不保证精确，不值得为它占一列 schema 与一次迁移。
 *
 * 构造只吃 [SharedPreferences]（与其它 Store 一致）：Hilt 在装配点调 [SettingsPrefs.of]，
 * JVM 测试直接塞 [com.cycling.rssradar.core.data.FakeSharedPreferences]。
 */
class ReadingPositionStore(private val prefs: SharedPreferences) {

    /** 没有记录时返回 null（调用方按「回到开头」处理，不要用 0 当哨兵——0 是合法位置）。 */
    fun get(articleId: Long): Float? {
        val saved = prefs.getFloat(key(articleId), MISSING)
        return if (saved.isNaN()) null else saved
    }

    /**
     * 记下这次的位置。比例落在 [ReadingPosition.isWorthRemembering] 之外（还在开头 / 已读完）
     * 时**清掉旧记录**：用户滚回顶部或读到底，就该按新语义处理，而不是留着上一次的中间位置。
     */
    fun save(articleId: Long, ratio: Float) {
        if (!ReadingPosition.isWorthRemembering(ratio)) {
            clear(articleId)
            return
        }
        val kept = order().filterNot { it == articleId }
        val next = (listOf(articleId) + kept).take(LIMIT)
        val dropped = kept.drop(LIMIT - 1)
        val editor = prefs.edit().putFloat(key(articleId), ratio)
            .putString(KEY_ORDER, next.joinToString(SEPARATOR))
        dropped.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    /** 删掉单篇的记录（文章被删除、清空文章、或位置不再值得记）。 */
    fun clear(articleId: Long) {
        val kept = order().filterNot { it == articleId }
        prefs.edit()
            .remove(key(articleId))
            .putString(KEY_ORDER, kept.joinToString(SEPARATOR))
            .apply()
    }

    private fun order(): List<Long> =
        prefs.getString(KEY_ORDER, null)
            ?.split(SEPARATOR)
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()

    private fun key(articleId: Long): String = "$KEY_PREFIX$articleId"

    private companion object {
        const val KEY_PREFIX = "reading_pos_"
        const val KEY_ORDER = "reading_pos_order"
        const val SEPARATOR = ","

        /** 最多记住多少篇。约 200 条 × 每条几十字节，量级可忽略。 */
        const val LIMIT = 200

        /** NaN 作「无记录」哨兵：任何合法比例（含 0）都不是 NaN。 */
        val MISSING = Float.NaN
    }
}
