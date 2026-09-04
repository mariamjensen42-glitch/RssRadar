package com.cycling.rssradar.data

import com.cycling.rssradar.data.db.ArticleDao
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.FeedDao
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.BestIconFinder
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 刷新子系统深模块（深化自原 FeedRepository）：订阅源刷新的全部规则都沉在这里，
 * 调用方（FeedRepository 门面、AutoSync）只见少数几个方法。
 *
 * 接口后藏着的实现规则（每一条都曾是散落的注释或调用方约定）：
 * - **双路径**：手动刷新全部源（refreshAll）；自动同步只刷 syncEnabled = 1 的源
 *   （refreshAutoSyncFeeds，issue #58「同步屏蔽」）。失败源静默跳过，保留已有数据。
 * - **用户状态保护**：按 link 增量 upsert 时只更新内容状态字段，已读/收藏/稍后读
 *   原样保留（CONTEXT.md「用户状态」，SQL 层由 ArticleDao.updateContentState 保证）。
 * - **有界并发**：Semaphore(8)（issue #48），HttpURLConnection 无状态、Room 写入串行。
 * - **图标回填**：仅 iconUrl 为 null 时抓，永不覆盖（CONTEXT.md「站点图标」），
 *   fire-and-forget，不占并发名额。
 *
 * 测试缝：DAO 是 Room 接口可手写 fake；[http] 与 [transactionRunner] 注入后，
 * 「刷新不覆盖用户状态」「并发度」等规则可纯 JVM 断言，不再依赖真机数据库。
 */
class RefreshEngine(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val parser: RssParser,
    private val http: HttpFetcher,
    private val transactionRunner: TransactionRunner = DirectTransactionRunner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 为 null 时站点图标抓取不可用。 */
    private val iconFinder: BestIconFinder? = null,
    /** fire-and-forget 图标抓取的外部作用域，为 null 时同样不抓。 */
    private val externalScope: CoroutineScope? = null,
) {

    companion object {
        /** 刷新的有界并发度（#48）：8 路并行，几百个源不再串行排队几十分钟。 */
        private const val REFRESH_CONCURRENCY = 8
    }

    private val refreshSemaphore = Semaphore(REFRESH_CONCURRENCY)

    // —— 对外接口：四条刷新路径，全部返回「成功源数 / 是否成功」，失败语义一致 ——

    /** 手动路径：刷新全部订阅源，供下拉刷新调用。 */
    suspend fun refreshAll(): Int = refreshInParallel(feedDao.getAll().map { it.id })

    /** 自动同步路径（issue #58）：只刷新参与自动同步的源（syncEnabled = 1）。 */
    suspend fun refreshAutoSyncFeeds(): Int =
        refreshInParallel(feedDao.getSyncEnabledFeedIds())

    /** 定向刷新一批订阅源（OPML 盲导后补文章用）。 */
    suspend fun refreshFeeds(feedIds: List<Long>): Int = refreshInParallel(feedIds)

    /** 单源刷新（订阅源文章列表顶栏动作用）。 */
    suspend fun refreshSingle(feedId: Long): Boolean = refreshFeed(feedId)

    /**
     * 抓取并解析一次 feed XML，供订阅链路（预览 probe / 添加 addFeed）复用。
     * 失败抛 [IllegalArgumentException]（非法 feed）或 [IOException]（网络），调用方转 UI 提示。
     */
    suspend fun fetchAndParse(url: String): RssParser.ParsedFeed = withContext(ioDispatcher) {
        http.fetch(url).use { parser.parse(it) }
    }

    /**
     * 订阅落库后的首批文章写入：与增量刷新共用同一条 upsert 路径，
     * 保证「新文章插入 / 已有文章只更新内容状态」的规则只有一份实现。
     */
    suspend fun persistArticles(feedId: Long, articles: List<RssParser.ParsedArticle>, fetchedAt: Long) {
        upsertArticles(feedId, articles, fetchedAt)
    }

    /**
     * 站点图标后台回填（fire-and-forget）：抓到即写库，UI 由 Room Flow 自动刷新。
     * 仅 [CONTEXT.md]「站点图标」语义：null 才抓，永不覆盖。任何失败静默放弃——图标是装饰性资产。
     */
    fun backfillIcon(feedId: Long, siteUrl: String) {
        val finder = iconFinder ?: return
        val scope = externalScope ?: return
        if (siteUrl.isBlank()) return
        scope.launch {
            try {
                finder.findIcon(siteUrl)?.let { feedDao.updateIconUrl(feedId, it) }
            } catch (_: Exception) {
                // 静默放弃
            }
        }
    }

    /**
     * 有界并发刷新（#48）：Semaphore(8) 同时处理 8 个源。
     * 整体跑在 ioDispatcher 上，不让并发骨架占用调用方（Main）线程。
     */
    private suspend fun refreshInParallel(feedIds: List<Long>): Int = withContext(ioDispatcher) {
        coroutineScope {
            feedIds.map { feedId ->
                async {
                    refreshSemaphore.withPermit { refreshFeed(feedId) }
                }
            }.awaitAll().count { it }
        }
    }

    /**
     * 增量刷新：重新抓取并按 link 更新文章的内容状态。
     * 绝不覆盖用户状态（已读/收藏/稍后读），见 CONTEXT.md「用户状态」。
     */
    private suspend fun refreshFeed(feedId: Long): Boolean = withContext(ioDispatcher) {
        val feed = feedDao.getById(feedId) ?: return@withContext false
        val parsed = try {
            fetchAndParse(feed.url)
        } catch (_: IllegalArgumentException) {
            return@withContext false
        } catch (_: IOException) {
            return@withContext false
        }
        upsertArticles(feedId, parsed.articles, System.currentTimeMillis())
        // 图标 backfill：老源 / 盲导源 / 早期订阅的源补齐（仅 null 时抓）
        if (feed.iconUrl == null) backfillIcon(feedId, parsed.siteUrl)
        true
    }

    /** 同一 link：只更新内容状态字段，用户状态原样保留。整源一次事务（#48）。 */
    private suspend fun upsertArticles(feedId: Long, articles: List<RssParser.ParsedArticle>, now: Long) {
        if (articles.isEmpty()) return
        transactionRunner.inTransaction {
            // 一次查询建 link→id 映射，替代逐篇 findIdByLink（#48：消除 N+1 写放大）
            val existing = articleDao.getIdLinkPairsByFeed(feedId).associate { it.link to it.id }
            // 墓碑过滤（归档/清空真删的文章）：feed XML 还挂着它们，不跳过就会「删了又回来」
            val tombstoned = articleDao.getTombstonedLinks(feedId).toHashSet()
            val newArticles = mutableListOf<ArticleEntity>()
            articles.forEach { article ->
                if (article.link in tombstoned) return@forEach
                val readingMinutes = article.contentText?.let { estimateReadingMinutes(it) }
                // 摘要级内容不当正文：contentSource 记 NONE，详情页才会去抓原文
                // （RssParser.FULL_TEXT_MIN_CHARS，详见该常量注释）
                val contentSource = if (RssParser.isFullText(article.contentHtml, article.contentText)) {
                    ArticleEntity.CONTENT_SOURCE_FEED
                } else {
                    ArticleEntity.CONTENT_SOURCE_NONE
                }
                val existingId = existing[article.link]
                if (existingId == null) {
                    newArticles += ArticleEntity(
                        feedId = feedId,
                        link = article.link,
                        title = article.title,
                        summary = article.summary,
                        content = article.contentHtml,
                        contentText = article.contentText,
                        author = article.author,
                        publishedAt = article.publishedAt,
                        fetchedAt = now,
                        coverUrl = article.coverUrl,
                        readingMinutes = readingMinutes,
                        contentSource = contentSource,
                        mediaKind = article.mediaKind,
                    )
                } else {
                    articleDao.updateContentState(
                        id = existingId,
                        title = article.title,
                        summary = article.summary,
                        content = article.contentHtml,
                        contentText = article.contentText,
                        author = article.author,
                        publishedAt = article.publishedAt,
                        coverUrl = article.coverUrl,
                        readingMinutes = readingMinutes,
                        contentSource = contentSource,
                        fetchedAt = now,
                        mediaKind = article.mediaKind,
                    )
                }
            }
            if (newArticles.isNotEmpty()) articleDao.insertAll(newArticles)
        }
    }
}

/**
 * 事务缝：Room 的 withTransaction 需要真库实例，包成可注入接口后，
 * JVM 测试用 [DirectTransactionRunner] 直跑，生产装配真事务。
 * 注意：普通 interface 而非 fun interface——Kotlin 的 fun interface
 * 不允许带类型参数的抽象方法。
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** 无事务直跑：仅测试用。 */
object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

/** 中文按 300 字/分钟，非 CJK 按 200 词/分钟，混排取较大值。来自真实正文字数，不虚构。 */
internal fun estimateReadingMinutes(text: String): Int {
    val cjkChars = text.count { it.code in 0x4E00..0x9FFF }
    val otherWords = text.count { !((it.code in 0x4E00..0x9FFF) || it.isWhitespace()) } / 6
    val minutes = maxOf(cjkChars / 300, otherWords / 200)
    return (minutes + 1).coerceAtLeast(1)
}
