package com.cycling.rssradar.core.domain.ai

/**
 * 阅读习惯的统计算法，纯函数、无依赖。
 *
 * 放在 domain 层且刻意不碰数据库：这些数字要**喂给模型去组织语言**，
 * 所以它们必须能脱离 Android 环境算出来并断言——
 * 「AI 只做意图识别与语言组织，数字必须真实」这条原则在这里的具体形态就是：
 * 数字由纯函数算，模型一个字都不许改。
 */
object AiReadingStats {

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS

    /**
     * 活跃时段：出现频次明显高于全天平均的小时，按频次降序后取前 [topN]，最后按小时升序返回。
     *
     * "明显高于平均"是个保守判据——平均线以下的小时说明不了习惯，
     * 把它们列进报告只会让"你的活跃时段是 0~23 点"这种废话出现。
     * 样本太少（比如只有 1 次打开）时结果就是 1 个小时，如实反映，不美化。
     */
    fun activeHours(
        timestamps: List<Long>,
        zoneOffsetMillis: Int,
        topN: Int = 5,
    ): List<Int> {
        if (timestamps.isEmpty()) return emptyList()

        val buckets = IntArray(24)
        timestamps.forEach { t ->
            val localMillis = (t + zoneOffsetMillis) % DAY_MS
            val hour = ((if (localMillis < 0) localMillis + DAY_MS else localMillis) / HOUR_MS).toInt()
            buckets[hour]++
        }

        val average = timestamps.size / 24.0
        return buckets
            .mapIndexed { hour, count -> hour to count }
            .filter { (_, count) -> count > 0 && count > average }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .take(topN)
            .map { it.first }
            .sorted()
    }

    /**
     * 订阅源集中度：归一化赫芬达尔指数，0 = 完全分散，1 = 全部集中在一个源。
     *
     * 用归一化版本而不是裸 HHI，是因为裸 HHI 的下界随订阅源数量变化
     * （10 个源的最分散状态是 0.1，100 个源是 0.01），
     * 直接拿给用户看会得出"订得越多越专注"的荒谬结论。
     */
    fun concentration(counts: List<Int>): Double {
        val positive = counts.filter { it > 0 }
        val total = positive.sum()
        if (total <= 0) return 0.0

        val n = positive.size
        if (n == 1) return 1.0

        val hhi = positive.sumOf { count ->
            val share = count.toDouble() / total
            share * share
        }
        return ((hhi - 1.0 / n) / (1.0 - 1.0 / n)).coerceIn(0.0, 1.0)
    }
}
