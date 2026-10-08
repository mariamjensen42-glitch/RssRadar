package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiSupportDao
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.data.db.dao.FeedDao
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleProposalCheck
import com.cycling.rssradar.core.domain.search.SearchQueryBuilder
import com.cycling.rssradar.core.model.AiFeature

/**
 * 「智能过滤规则生成」的取数入口：把用户的一句自然语言变成**可复核的规则提案**。
 *
 * 为什么不是简单地"调模型拿 JSON"：这条链路的后果是单向的——规则一旦启用，命中就隐藏，
 * 用户此后看不到那些文章，也就无从发现自己被屏蔽了什么。所以这里做了两件模型之外的事：
 * ① 给出**候选标题清单**（`hits` 的唯一合法来源，没有清单模型只能编）；
 * ② 拿**与线上同一套** [com.cycling.rssradar.core.domain.filter.FilterRuleEngine]
 * 在真实候选上复算一遍，把模型举例里对不上的挑出来（见 [RuleProposalCheck]）。
 *
 * 本类只负责"生成 + 复核"，**不写规则**：启用与否由用户在看过后决定。
 */
class AiFilterRuleDrafter(
    private val runner: AiFeatureRunner,
    private val supportDao: AiSupportDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
) {

    /** 一条待确认的规则提案。 */
    data class Proposal(
        /** 关键词，同时用作规则名与 pattern（用户可在编辑器里再改）。 */
        val keyword: String,
        val fields: Set<RuleField>,
        /** 模型声称会命中的标题。列出来是为了让用户能当场对照。 */
        val claimedHits: List<String>,
        /** 本地实算命中的候选标题。 */
        val verifiedHits: List<String>,
        /** 模型给了、本地对不上的标题——它们是这条规则"推理链断了"的证据。 */
        val fabricated: List<String>,
        /** 这次到底验没验（纯摘要规则没有标题可查 ⇒ false）。 */
        val checked: Boolean,
        /** 全库 LIKE 命中数，用来看这条规则的影响面。**是估算不是精确值**。 */
        val libraryCount: Int,
    )

    /**
     * 生成结果：要么给提案，要么给一句能直接展示的原因。
     *
     * 两者都为空是合法状态（模型这次没产出可用规则），由界面决定怎么措辞——
     * 这里不编一句"未生成任何规则"塞进 [problem]，那会让界面分不清"失败"和"模型很克制"。
     */
    data class Draft(
        val proposals: List<Proposal> = emptyList(),
        val problem: String? = null,
    )

    suspend fun draft(description: String): Draft {
        val request = description.trim()
        if (request.isEmpty()) return Draft(problem = "请先描述你不想看到什么")

        val candidates = recentCandidates()
        // 清单为空就别调模型了：它是 hits 的唯一来源，没清单必然换来一堆编造的示例，
        // 那既花了额度又给出一个不能用的结果。
        if (candidates.isEmpty()) {
            return Draft(problem = "最近没有可用于举例的文章，先刷新订阅再来生成")
        }

        val context = AiPromptContext(
            title = "过滤规则生成",
            feedTitle = "本地文章清单",
            question = request,
            companions = candidates,
        )

        return when (val outcome = runner.runWithContext(AiFeature.FILTER_RULE, SUBJECT_ID, context)) {
            is AiFeatureRunner.Outcome.Success -> Draft(proposals = buildProposals(outcome.payload, candidates))
            is AiFeatureRunner.Outcome.Skipped -> Draft(problem = outcome.reason)
            is AiFeatureRunner.Outcome.Failed -> Draft(problem = outcome.message)
            AiFeatureRunner.Outcome.OutOfBudget -> Draft(problem = "今日 AI 额度已用完，明天再试")
        }
    }

    private suspend fun buildProposals(payload: String, candidates: List<AiPromptCompanion>): List<Proposal> {
        val titles = candidates.map { it.title }
        return AiParsers.filterRule(payload).rules.map { rule ->
            val fields = filterRuleFields(rule.field)
            val check = RuleProposalCheck.check(rule.keyword, fields, rule.hits, titles)
            Proposal(
                keyword = rule.keyword,
                fields = fields,
                claimedHits = rule.hits,
                verifiedHits = check.matched,
                fabricated = check.fabricated,
                checked = check.checked,
                libraryCount = libraryCountOf(rule.keyword),
            )
        }
    }

    /** 候选清单：近期抓取、有内容可判的文章，只取 id 与标题（与跨文章功能同一形状）。 */
    private suspend fun recentCandidates(): List<AiPromptCompanion> {
        val ids = supportDao.processableIdsSince(
            System.currentTimeMillis() - CANDIDATE_WINDOW_MS,
            CANDIDATE_LIMIT,
        )
        if (ids.isEmpty()) return emptyList()
        val feedTitles = feedDao.getAll().associate { it.id to it.title }
        return supportDao.briefsOf(ids).map { brief ->
            AiPromptCompanion(
                id = brief.id,
                title = brief.title,
                feedTitle = feedTitles[brief.feedId].orEmpty(),
            )
        }
    }

    /**
     * 库内影响面。
     *
     * 与规则编辑页的「预计命中」用同一条查询（标题/摘要/正文三处 LIKE），口径一致——
     * 两处各写一套估算，用户会看到同一个词在编辑页和这里给出两个数字。
     * LIKE 无条件覆盖三列，所以它是**上界估算**，界面文案必须写"约"。
     */
    private suspend fun libraryCountOf(keyword: String): Int = runCatching {
        articleDao.countMatching("%${SearchQueryBuilder.escapeLike(keyword)}%")
    }.getOrDefault(0)

    private companion object {
        /**
         * FILTER_RULE 挂在**全局**（规则作用于整个订阅列表），所以 subjectId 只能是 0。
         *
         * 这次调用仍会落一条产物（用量归因要它），但它没有对应的父主体——
         * 与每日任务写的全局产物同一处境，由时间滚动清理。
         */
        const val SUBJECT_ID = 0L

        /** 候选窗口与条数：够模型找出有代表性的示例，又不会把 prompt 撑到没边。 */
        const val CANDIDATE_WINDOW_MS = 7 * 86_400_000L
        const val CANDIDATE_LIMIT = 120
    }
}

/**
 * 模型给的字段标签 → 规则字段集合。
 *
 * `BOTH` 展开成标题 + 摘要而不是"全部字段"：模型的词汇表里只有这两项，
 * 把它当成"全字段"会让规则顺带扫正文，命中面比用户看到的大得多。
 */
fun filterRuleFields(field: String): Set<RuleField> = when (field.trim().uppercase()) {
    "TITLE" -> setOf(RuleField.TITLE)
    "SUMMARY" -> setOf(RuleField.SUMMARY)
    else -> setOf(RuleField.TITLE, RuleField.SUMMARY)
}
