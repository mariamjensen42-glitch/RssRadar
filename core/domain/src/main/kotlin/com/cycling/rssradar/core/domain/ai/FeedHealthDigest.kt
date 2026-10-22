package com.cycling.rssradar.core.domain.ai

/**
 * AI 源健康判定（订阅页「AI 源健康」区的唯一判据）。
 *
 * **它和 [com.cycling.rssradar.core.domain.rss.FeedHealth] 是两回事，刻意不合并**：
 * - `FeedHealth` 判的是「抓不抓得到」——DNS / 证书 / 4xx 这类错误本身是确定性的，
 *   所以有精确分类与两档阈值，判定完全本地、不需要模型。
 * - 这里判的是「抓得到，但内容还对不对」——更新频率明显下降、正文长期抓不完整、
 *   源悄悄变成营销号。这类问题没有确定性判据，只能由模型读统计数字给出。
 *
 * 两者会同时成立（源真挂了：本地记失败、AI 也判 BROKEN），那不是重复而是互为印证。
 * 但**把本地判据当成唯一入口是错的**——那会永久丢掉"抓取一切正常、内容已经不值得读"
 * 这一整类问题，而它恰好是订阅源腐坏最常见的样子。
 *
 * 纯函数：输入是已解析好的判定、输出可断言的结构，不碰数据库、不碰 Android，可纯 JVM 测。
 */
object FeedHealthDigest {

    /**
     * 模型给出的健康档。取值与 `AiParsers.feedHealth` 归一化后的结果一一对应。
     */
    enum class Status(val raw: String) {
        OK("OK"),
        DEGRADED("DEGRADED"),
        BROKEN("BROKEN"),
        UNKNOWN("UNKNOWN"),
        ;

        companion object {
            /** 无法识别的取值一律归 UNKNOWN：模型偶尔会自创档位，不能让它漏进列表。 */
            fun of(raw: String): Status =
                entries.firstOrNull { it.raw.equals(raw.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }

    /** 一条判定。标题不在这里——纯函数不该认识「源名」这种东西，由调用方另行解析。 */
    data class Verdict(
        val feedId: Long,
        val status: Status,
        val reason: String,
        val advice: String,
        val createdAt: Long,
    )

    /**
     * 需要处理的判定：**只含 BROKEN 与 DEGRADED**。
     *
     * OK 不需要报——用户不必被告知"一切都好"，那一区的存在感应该只来自问题。
     * UNKNOWN 也不报：它是模型在说"数据不足，我判断不了"，把它当问题列出来
     * 等于把"我没把握"包装成"你的源有问题"。
     *
     * 排序：失效先于降频（BROKEN 更要紧），同档内最近判定的在前。
     * 同一个源只留最新一条由产物表主键（subjectKind, subjectId, kind）保证，这里不额外去重。
     */
    fun actionable(verdicts: List<Verdict>): List<Verdict> =
        verdicts
            .filter { it.status == Status.BROKEN || it.status == Status.DEGRADED }
            .sortedWith(compareBy<Verdict> { severity(it.status) }.thenByDescending { it.createdAt })

    /** 档位严重度：数字小的排前面。只有 BROKEN / DEGRADED 会进 [actionable]，其余给同一档即可。 */
    private fun severity(status: Status): Int = when (status) {
        Status.BROKEN -> 0
        Status.DEGRADED -> 1
        Status.OK -> 2
        Status.UNKNOWN -> 3
    }
}
