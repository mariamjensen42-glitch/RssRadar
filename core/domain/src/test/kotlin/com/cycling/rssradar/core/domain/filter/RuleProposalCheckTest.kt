package com.cycling.rssradar.core.domain.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则提案复核的守门测试。
 *
 * 锁的是"用户看不见但会吃亏"的那条线：复核一旦把编造的示例算成通过，
 * 用户就会启用一条凭空屏蔽的规则，而此后他再也看不到被屏蔽了什么。
 */
class RuleProposalCheckTest {

    private val candidates = listOf(
        "【剧透】某电影结局分析",
        "本周抽奖活动汇总",
        "招聘：Kotlin 后端工程师",
        "剧透预警：这部剧的第三集",
    )

    @Test
    fun `本地实算命中而不是采信模型举例`() {
        val result = RuleProposalCheck.check(
            keyword = "剧透",
            fields = setOf(RuleField.TITLE),
            claimedHits = listOf("【剧透】某电影结局分析"),
            candidates = candidates,
        )
        assertTrue(result.checked)
        assertEquals(
            listOf("【剧透】某电影结局分析", "剧透预警：这部剧的第三集"),
            result.matched,
        )
        assertEquals(emptyList<String>(), result.fabricated)
    }

    @Test
    fun `模型举例里清单外或规则不命中的都算编造`() {
        val result = RuleProposalCheck.check(
            keyword = "剧透",
            fields = setOf(RuleField.TITLE),
            claimedHits = listOf(
                "【剧透】某电影结局分析",
                "某电影结局深度解析",
                "本周抽奖活动汇总",
            ),
            candidates = candidates,
        )
        assertEquals(
            listOf("某电影结局深度解析", "本周抽奖活动汇总"),
            result.fabricated,
        )
    }

    @Test
    fun `纯摘要规则如实报未校验而不是判编造`() {
        val result = RuleProposalCheck.check(
            keyword = "赞助",
            fields = setOf(RuleField.SUMMARY),
            claimedHits = listOf("本周抽奖活动汇总"),
            candidates = candidates,
        )
        assertFalse(result.checked)
        assertEquals(emptyList<String>(), result.matched)
        assertEquals(emptyList<String>(), result.fabricated)
    }

    @Test
    fun `同时看标题与摘要时按标题校验`() {
        val result = RuleProposalCheck.check(
            keyword = "招聘",
            fields = setOf(RuleField.TITLE, RuleField.SUMMARY),
            claimedHits = listOf("招聘：Kotlin 后端工程师"),
            candidates = candidates,
        )
        assertTrue(result.checked)
        assertEquals(listOf("招聘：Kotlin 后端工程师"), result.matched)
    }

    @Test
    fun `空关键词不进入校验也不会误判`() {
        val result = RuleProposalCheck.check("   ", setOf(RuleField.TITLE), listOf("任意"), candidates)
        assertFalse(result.checked)
        assertEquals(emptyList<String>(), result.fabricated)
    }

    @Test
    fun `关键词做子串匹配与线上引擎一致`() {
        val result = RuleProposalCheck.check(
            keyword = "抽奖",
            fields = setOf(RuleField.TITLE),
            claimedHits = emptyList(),
            candidates = candidates,
        )
        assertEquals(listOf("本周抽奖活动汇总"), result.matched)
    }
}
