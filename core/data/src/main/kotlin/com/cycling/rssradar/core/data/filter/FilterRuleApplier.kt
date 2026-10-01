package com.cycling.rssradar.core.data.filter

import com.cycling.rssradar.core.data.ArticleCleaner
import com.cycling.rssradar.core.data.db.dao.ArticleDao
import com.cycling.rssradar.core.domain.filter.FilterRuleEngine
import com.cycling.rssradar.core.domain.filter.RuleTarget

/**
 * 把过滤规则作用到**存量文章**上（新文章在刷新入库时判定，见 [com.cycling.rssradar.core.data.RefreshEngine]）。
 *
 * 为什么必须有这一半：用户配一条「标题含剧透就隐藏」，期待的是立刻生效，
 * 而不是"从下一条新闻开始生效"。少了它，规则页上的"预计命中 N 篇"就是个空承诺。
 *
 * 两段式：先**扫完**再**执行**。扫描期间不动库——HIDE 会真删行，边扫边删会让
 * 按 id 游标推进的扫描跳过后续文章（同一批里删掉的行会让结果集收缩）。
 *
 * 豁免：收藏与稍后读不吃 HIDE。抓名单与删除用的是同一套条件（DAO 层写死），
 * 所以统计到的命中数与实际删除数可能不等——那是豁免，不是 bug。
 */
class FilterRuleApplier(
    private val articleDao: ArticleDao,
    private val cleaner: ArticleCleaner,
) {

    data class Result(
        val hidden: Int = 0,
        val markedRead: Int = 0,
        val starred: Int = 0,
        val bookmarked: Int = 0,
    ) {
        val total: Int get() = hidden + markedRead + starred + bookmarked
    }

    private data class Buckets(
        val hidden: List<Long>,
        val read: List<Long>,
        val star: List<Long>,
        val bookmark: List<Long>,
    )

    /** 规则保存前/修改后的「预计命中」。只读，不改库。 */
    suspend fun preview(engine: FilterRuleEngine): Result {
        val buckets = scan(engine)
        return Result(
            hidden = buckets.hidden.size,
            markedRead = buckets.read.size,
            starred = buckets.star.size,
            bookmarked = buckets.bookmark.size,
        )
    }

    /** 实际执行。返回**真实影响**的条数（归档会因豁免少于扫描命中数）。 */
    suspend fun apply(engine: FilterRuleEngine): Result {
        val buckets = scan(engine)
        val now = System.currentTimeMillis()
        var hidden = 0
        buckets.hidden.chunked(BATCH).forEach { hidden += cleaner.archiveByIds(it, now) }
        var read = 0
        buckets.read.chunked(BATCH).forEach { read += articleDao.markReadBatch(it) }
        var starred = 0
        buckets.star.chunked(BATCH).forEach { starred += articleDao.starBatch(it, now) }
        var bookmarked = 0
        buckets.bookmark.chunked(BATCH).forEach { bookmarked += articleDao.bookmarkBatch(it, now) }
        return Result(hidden, read, starred, bookmarked)
    }

    private suspend fun scan(engine: FilterRuleEngine): Buckets {
        val hidden = mutableListOf<Long>()
        val read = mutableListOf<Long>()
        val star = mutableListOf<Long>()
        val bookmark = mutableListOf<Long>()
        var afterId = 0L
        while (true) {
            val rows = articleDao.scanForRules(afterId, BATCH)
            if (rows.isEmpty()) break
            afterId = rows.last().id
            for (row in rows) {
                val outcome = engine.evaluate(
                    RuleTarget(
                        feedId = row.feedId,
                        groupName = row.feedGroup,
                        title = row.title,
                        summary = row.summary.orEmpty(),
                        content = row.contentText.orEmpty(),
                        author = row.author.orEmpty(),
                    ),
                )
                if (outcome.isEmpty) continue
                if (outcome.hidden && !row.isStarred && !row.isBookmarked) hidden += row.id
                if (outcome.markRead) read += row.id
                if (outcome.star && !row.isStarred) star += row.id
                if (outcome.bookmark && !row.isBookmarked) bookmark += row.id
            }
        }
        return Buckets(hidden, read, star, bookmark)
    }

    private companion object {
        /** 与索引重建同量级：一批 500 行，数万篇也就几十轮。 */
        const val BATCH = 500
    }
}
