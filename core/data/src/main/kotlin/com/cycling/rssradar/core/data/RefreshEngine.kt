package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.data.parser.RssParser
import com.cycling.rssradar.core.data.rss.BestIconFinder
import com.cycling.rssradar.core.data.rss.FeedDiscovery
import com.cycling.rssradar.core.domain.rss.ConditionalFetchResult
import com.cycling.rssradar.core.domain.rss.ConditionalHttpFetcher
import com.cycling.rssradar.core.domain.rss.FeedFailureCategory
import com.cycling.rssradar.core.domain.rss.FeedProbeResult
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.retryOnSlowResponse
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * 刷新子系统深模块（深化自原 FeedRepository）：订阅源刷新的全部规则都沉在这里，
 * 调用方（FeedRepository 门面、AutoSync）只见少数几个方法。
 *
 * 接口后藏着的实现规则（每一条都曾是散落的注释或调用方约定）：
 * - **双路径**：手动刷新全部源（refreshAll）；自动同步只刷 syncEnabled = 1 的源
 *   （refreshAutoSyncFeeds，issue #58「同步屏蔽」）。失败源静默跳过，保留已有数据。
 * - **用户状态保护**：按 link 增量 upsert 时只更新内容状态字段，已读/收藏/稍后读
 *   原样保留（CONTEXT.md「用户状态」，SQL 层由 ArticleDao.updateContentState 保证）。
 * - **有界并发**：Semaphore(32)（#48 引入，2026-09-06 提到 32），HttpURLConnection 无状态、Room 写入串行。
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
    /**
     * 条件请求能力（ETag / Last-Modified 协商）。null 时回落到无条件抓取（旧路径）。
     * 非 null 时每源刷新先带凭证发 If-None-Match / If-Modified-Since，304 直接跳过。
     */
    private val conditionalHttp: ConditionalHttpFetcher? = null,
    /**
     * 自愈留痕缝：地址被自动改写时回调 (feedId, 旧地址, 新地址)。
     * 默认空实现——core/data 不依赖日志框架与 UI，由装配层决定「怎么让用户看见」。
     */
    private val onHealed: suspend (feedId: Long, oldUrl: String, newUrl: String) -> Unit = { _, _, _ -> },
) {

    companion object {
        /**
         * 刷新的有界并发度：HttpURLConnection 每请求一线程式的轻量协程阻塞，
         * 32 路在百级源场景下把总耗时压到 1/4（原 8 路是 #48 保守值，真机反馈仍嫌慢）。
         * 瓶颈在网络 IO 不在本机资源，再往上收益递减且容易触发站点限流。
         */
        const val REFRESH_CONCURRENCY = 32

        /**
         * 自愈尝试的连续失败门槛：第一次失败可能是站点临时抽风，直接花 N 个请求做
         * 发现是浪费；连续 2 次（与 FeedHealth 的失效判定对齐）还解析不出才值得修。
         */
        const val HEAL_FAILURE_THRESHOLD = 2

        /**
         * 每轮刷新的自愈配额：单个坏源最多 1（抓 HTML）+ 8（候选校验）+ 1（补刷新）
         * 个请求，而且全程占着并发名额（最坏 10 × 20s 读超时）。700 源里若有一批坏源，
         * 无配额能把全量刷新从几分钟拖到几十分钟，后台自动同步还白烧移动流量。
         */
        const val MAX_HEALS_PER_ROUND = 10
    }

    private val refreshSemaphore = Semaphore(REFRESH_CONCURRENCY)

    /** 本轮刷新剩余的自愈名额（[refreshInParallel] / [refreshSingle] 每轮重置）。 */
    private val healBudget = AtomicInteger(MAX_HEALS_PER_ROUND)

    /**
     * 占一个自愈名额。并发下用 CAS 而不是先 get 再 set：先判断再减会超发，
     * 超发就意味着「配额」名存实亡。
     */
    private fun claimHealSlot(): Boolean {
        while (true) {
            val current = healBudget.get()
            if (current <= 0) return false
            if (healBudget.compareAndSet(current, current - 1)) return true
        }
    }

    // —— 对外接口：四条刷新路径，全部返回「成功源数 / 是否成功」，失败语义一致 ——

    /** 手动路径：刷新全部订阅源，供下拉刷新调用。[onProgress] 每 完成一个源 回调 (done, total)。 */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        refreshInParallel(feedDao.getAll().map { it.id }, onProgress)

    /** 自动同步路径（issue #58）：只刷新参与自动同步的源（syncEnabled = 1）。 */
    suspend fun refreshAutoSyncFeeds(): Int =
        refreshInParallel(feedDao.getSyncEnabledFeedIds())

    /** 定向刷新一批订阅源（OPML 盲导后补文章用）。 */
    suspend fun refreshFeeds(feedIds: List<Long>): Int = refreshInParallel(feedIds)

    /** 单源刷新（订阅源文章列表顶栏动作用）：手动动作，独占本轮自愈名额（1 个就够）。 */
    suspend fun refreshSingle(feedId: Long): Boolean {
        healBudget.set(1)
        return refreshFeed(feedId)
    }

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
     * 有界并发刷新（#48）：Semaphore(32) 同时处理 32 个源。
     * 整体跑在 ioDispatcher 上，不让并发骨架占用调用方（Main）线程。
     * [onProgress]：多路并发下回调可能乱序交错，但 done 单调递增（AtomicInteger），
     * 708 源全量刷新可达数十分钟——没有进度用户无法区分「在跑」和「卡死」。
     */
    private suspend fun refreshInParallel(
        feedIds: List<Long>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(ioDispatcher) {
        coroutineScope {
            val total = feedIds.size
            healBudget.set(MAX_HEALS_PER_ROUND)
            val done = AtomicInteger(0)
            feedIds.map { feedId ->
                async {
                    refreshSemaphore.withPermit {
                        try {
                            refreshFeed(feedId)
                        } finally {
                            onProgress(done.incrementAndGet(), total)
                        }
                    }
                }
            }.awaitAll().count { it }
        }
    }

    /**
     * 增量刷新：重新抓取并按 link 更新文章的内容状态。
     * 绝不覆盖用户状态（已读/收藏/稍后读），见 CONTEXT.md「用户状态」。
     *
     * 源级捷径：有条件请求能力时先做协商（304 = 源没变，直接算成功返回，
     * 零下载零解析零写库）；凭证在每次 200 后更新，服务器没回就清空。
     *
     * 失效检测埋点（#82）：失败按 [FeedProbeResult.from] 归类后写 feeds 失败计数
     * （分类只有一处——订阅预览和刷新共用同一份 from() 映射）；任何一次成功清零。
     * 判定阈值在 core/domain FeedHealth，这里只负责记数。
     */
    private suspend fun refreshFeed(feedId: Long): Boolean = withContext(ioDispatcher) {
        val feed = feedDao.getById(feedId) ?: return@withContext false
        val ok = try {
            // 刷新链路也吃「等响应超时重试」：此前只有订阅预览有这个待遇，导致
            // RSSHub 冷路由第一次刷新读超时 → 记一次失败，用户手动再刷也撞同一堵墙。
            // 重试只针对 isRetryableTimeout（第二次常命中实例缓存秒回），
            // upsert 按 link 幂等，重入无副作用。CancellationException 在 retryOnSlowResponse
            // 里非可重试类会原样上抛，下方 catch 再放行。
            retryOnSlowResponse { doRefreshFeed(feed) }
        } catch (e: CancellationException) {
            // 协程取消不是源失败：不能把「刷新被取消」记成一次连续失败（假数据）
            throw e
        } catch (e: Exception) {
            feedDao.recordRefreshFailure(feedId, FeedFailureCategory.from(FeedProbeResult.from(e)).stored)
            // 失效自愈（对标 ReadYou 地址纠错）：抓得到但解析不出（INVALID_FEED）大概率是
            // OPML 盲导进了站点首页/已迁移地址。连续失败达到阈值才试（不给每次刷新都白付
            // 一次发现成本）；自愈换地址后立即补一轮刷新，成功走下方统一的恢复清零。
            val canHeal = FeedProbeResult.from(e) == FeedProbeResult.InvalidFeed &&
                feed.consecutiveFailures + 1 >= HEAL_FAILURE_THRESHOLD &&
                claimHealSlot()
            if (canHeal) healAndRefresh(feed) else false
        }
        // 写放大守门（1000+ 源全量刷新）：只有「确实有失败要清」或「从未记过成功」
        // 才写一次——健康源的常规刷新（200/304）对 feeds 表零额外写入。
        // 判定用刷新前快照：并发重复清零无副作用，漏清一次由下一轮刷新补上。
        if (ok && (feed.consecutiveFailures > 0 || feed.lastSuccessAt == null)) {
            feedDao.recordRefreshSuccess(feedId, System.currentTimeMillis())
        }
        ok
    }

    /** 自愈换地址成功后立即补一轮刷新；自愈失败或补刷新失败都返回 false。 */
    private suspend fun healAndRefresh(feed: FeedEntity): Boolean {
        val healed = tryHealUrl(feed) ?: return false
        return try {
            doRefreshFeed(healed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 失效源自愈：旧地址抓回来的是 HTML（或其他非 feed 内容）时，按 #5 的
     * autodiscovery 规则找候选（link rel=alternate 声明 → 常见路径兜底），
     * 逐个「真能解析出文章」验证，命中即改写 feed.url（[feedDao.updateUrl]
     * 顺带清掉旧地址的协商凭证）。目标地址已被订阅则跳过——不制造重复源。
     *
     * 找不到就返回 null，调用方维持失败现状；本函数自身的一切失败也返回 null
     * ——自愈是尽力而为的修复，绝不能把网络抖动放大成新问题。
     */
    private suspend fun tryHealUrl(feed: FeedEntity): FeedEntity? {
        val candidates = try {
            // charset 交给 jsoup 按 BOM / meta 探测（国内大量首页是 GBK，硬解 UTF-8 全乱码）
            val declared = http.fetch(feed.url).use { FeedDiscovery.candidateLinks(feed.url, it) }
            (declared + FeedDiscovery.guessedLinks(feed.url))
                .distinct()
                .take(FeedDiscovery.MAX_CANDIDATES)
        } catch (e: CancellationException) {
            throw e // 取消不是源失败：绝不能把「刷新被取消」咽下去继续打 8 个候选请求
        } catch (_: Exception) {
            return null
        }
        // 同主机优先：跨主机候选（站点把 feed 托管到第三方）可能是另一个源，
        // 排在后面当兜底，不抢在主 feed 前面改写地址（sortedBy 稳定，组内保序）。
        val feedHost = hostOf(feed.url)
        for (candidate in candidates.sortedBy { hostOf(it) != feedHost }) {
            if (feedDao.findIdByUrl(candidate) != null) continue
            val parsed = try {
                http.fetch(candidate).use { parser.parse(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            if (parsed.articles.isEmpty()) continue
            try {
                feedDao.updateUrl(feed.id, candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // feeds.url 有唯一索引：并发刷新下可能与另一个源撞车，撞了就换下一个候选
                continue
            }
            // 留痕缝：自愈会静默改写订阅地址，没有它用户只会觉得「这个源内容变了」
            onHealed(feed.id, feed.url, candidate)
            // 凭证已由 updateUrl 清空，内存副本同步置空：补刷新才会发无条件请求
            return feed.copy(url = candidate, etag = null, lastModified = null)
        }
        return null
    }

    /** 主机比较用；解析不出（非法 URL）按「不同主机」处理，保守排后面。 */
    private fun hostOf(url: String): String? =
        runCatching { java.net.URL(url).host }.getOrNull()

    /**
     * 单次「抓取 → 写库」本体：异常一律上抛给 [refreshFeed] 统一归类，
     * 这里不再 catch 成 boolean——那样失败原因（DNS/超时/4xx…）就丢了。
     */
    private suspend fun doRefreshFeed(feed: FeedEntity): Boolean {
        val conditional = conditionalHttp
        if (conditional != null) {
            val result = conditional.fetchConditional(feed.url, feed.etag, feed.lastModified)
            when (result) {
                is ConditionalFetchResult.NotModified -> return true // 304：内容未变
                is ConditionalFetchResult.Modified -> {
                    val parsed = result.body.use { parser.parse(it) }
                    upsertArticles(feed.id, parsed.articles, System.currentTimeMillis())
                    if (feed.iconUrl == null) backfillIcon(feed.id, parsed.siteUrl)
                    feedDao.updateValidators(feed.id, result.etag, result.lastModified)
                    return true
                }
            }
        } else {
            val parsed = fetchAndParse(feed.url)
            upsertArticles(feed.id, parsed.articles, System.currentTimeMillis())
            // 图标 backfill：老源 / 盲导源 / 早期订阅的源补齐（仅 null 时抓）
            if (feed.iconUrl == null) backfillIcon(feed.id, parsed.siteUrl)
            return true
        }
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
                // （判定唯一落点：ContentQualification）
                val contentSource = ContentQualification.contentSourceFor(
                    article.contentHtml,
                    article.contentText,
                )
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
