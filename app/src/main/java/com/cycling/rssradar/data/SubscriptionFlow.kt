package com.cycling.rssradar.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URL
import com.cycling.rssradar.core.domain.rss.FeedProbeResult
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.normalizeHttpUrl
import com.cycling.rssradar.core.domain.rss.retryOnSlowResponse
import com.cycling.rssradar.data.db.AppDatabase
import com.cycling.rssradar.data.db.DEFAULT_GROUP
import com.cycling.rssradar.data.db.FeedEntity
import com.cycling.rssradar.data.opml.OpmlEntry
import com.cycling.rssradar.data.opml.OpmlParser
import com.cycling.rssradar.data.opml.OpmlWriter
import com.cycling.rssradar.data.parser.RssParser
import com.cycling.rssradar.data.rss.FeedDiscovery

/** 订阅结果，供 UI 层区分提示文案。 */
sealed interface AddFeedResult {
    data object Success : AddFeedResult
    data object Duplicate : AddFeedResult
    data object InvalidFeed : AddFeedResult
    data object NetworkError : AddFeedResult
}

/**
 * 自动发现出来的一条 feed（#5）。字段全部来自真实抓取解析：
 * [title] 是 feed 自己的标题，[articleCount] 是解析出的文章数——不猜、不补默认值。
 */
data class DiscoveredFeed(
    val url: String,
    val title: String,
    val articleCount: Int,
)

/** OPML 盲导结果（ADR-0004）。 */
data class OpmlImportResult(
    val imported: Int,
    val skipped: Int,
    /** 新入库订阅源的 id，供定向刷新补文章。 */
    val newFeedIds: List<Long>,
    /** OPML 中出现的所有非空分组名，供调用方注册进分组注册表。 */
    val groups: Set<String>,
)

/**
 * 订阅链路深模块：发现 → 预览 → 落库 → OPML 导入/导出，一次订阅动作的全部规则。
 *
 * 从 FeedRepository 抽出来：那里 interface 近 50 个成员（查询/分页/标记/分组/清理…），
 * 订阅链路混在里面既难找也难测——现在 interface 收窄为 6 个方法，缝复用
 * [HttpFetcher] 与 [RefreshEngine.fetchAndParse]，测试与调用方走同一张小 interface。
 */
class SubscriptionFlow(
    database: AppDatabase,
    private val engine: RefreshEngine,
    /** 站点 HTML 抓取（feed 自动发现 #5 用）。与刷新链路同一条 HTTP 缝，测试可塞 fake。 */
    private val http: HttpFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val feedDao = database.feedDao()

    /**
     * Feed 自动发现（#5）：用户贴一个网址（可能只是站点首页），探测出可订阅的 feed。
     *
     * 三步降级，每步都靠"真能解析出文章"来判定，不看 Content-Type 之类不可靠的信号：
     * 1. 该地址本身就是 feed → 直接返回一条；
     * 2. 抓 HTML，读 `<link rel=alternate>` 声明的候选 → 逐个校验；
     * 3. 站点没声明 → 试常见路径（/feed、/rss.xml…）→ 逐个校验。
     *
     * 返回按"文章数多的在前"排序（主 feed 通常更全）。全部失败返回空表——
     * 不猜、不返回没验证过的地址。
     */
    suspend fun discoverFeeds(rawUrl: String): List<DiscoveredFeed> = withContext(ioDispatcher) {
        val url = normalizeHttpUrl(rawUrl) ?: return@withContext emptyList()
        // 1) 本身就是 feed
        runCatching { engine.fetchAndParse(url) }.getOrNull()?.let { parsed ->
            if (parsed.articles.isNotEmpty()) {
                return@withContext listOf(
                    DiscoveredFeed(url = url, title = parsed.title, articleCount = parsed.articles.size),
                )
            }
        }
        // 2) 站点 HTML 里声明的候选
        val html = runCatching { http.fetch(url).use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrNull() ?: return@withContext emptyList()
        val declared = FeedDiscovery.candidateLinks(url, html)
        val candidates = declared.ifEmpty { FeedDiscovery.guessedLinks(url) }
        val verified = ArrayList<DiscoveredFeed>()
        for (candidate in candidates.distinct().take(FeedDiscovery.MAX_CANDIDATES)) {
            verifyFeed(candidate)?.let { verified += it }
        }
        verified.sortByDescending { it.articleCount }
        verified
    }

    /** 校验一个候选地址：抓下来能解析出文章才算数。 */
    private suspend fun verifyFeed(url: String): DiscoveredFeed? {
        val parsed = runCatching { engine.fetchAndParse(url) }.getOrNull() ?: return null
        if (parsed.articles.isEmpty()) return null
        return DiscoveredFeed(url = url, title = parsed.title, articleCount = parsed.articles.size)
    }

    /**
     * 仅抓取+解析，用于"添加订阅"页的实时预览。不写入数据库。
     * 返回 [FeedProbeResult] 供 ViewModel 决定 UI 状态。
     *
     * 重试在 [fetchParsed] / [retryOnSlowResponse] 里，这里不再叠一层——
     * 曾经两层各重试一次，实际会发 4 次请求。
     */
    suspend fun probeFeed(rawUrl: String): FeedProbeResult = withContext(ioDispatcher) {
        val url = normalizeHttpUrl(rawUrl)
            ?: return@withContext FeedProbeResult.InvalidUrl
        runCatching { fetchParsed(url) }.fold(
            onSuccess = { FeedProbeResult.Valid(it.articles.size) },
            onFailure = FeedProbeResult::from,
        )
    }

    /**
     * 抓取+解析，等响应超时自动重试一次——订阅链路（预览 / 落库）共用，
     * 免得用户在预览时看着好好的，点「订阅」那一下反而撞上冷路由失败。
     */
    private suspend fun fetchParsed(url: String): RssParser.ParsedFeed =
        retryOnSlowResponse { engine.fetchAndParse(url) }

    suspend fun addFeed(
        rawUrl: String,
        groupName: String = DEFAULT_GROUP,
        sourceType: Int = FeedEntity.SOURCE_TYPE_RSS,
    ): AddFeedResult = withContext(ioDispatcher) {
        val url = normalizeHttpUrl(rawUrl) ?: return@withContext AddFeedResult.InvalidFeed

        if (feedDao.findIdByUrl(url) != null) return@withContext AddFeedResult.Duplicate

        val parsed = try {
            fetchParsed(url)
        } catch (_: IllegalArgumentException) {
            return@withContext AddFeedResult.InvalidFeed
        } catch (_: IOException) {
            return@withContext AddFeedResult.NetworkError
        }

        val now = System.currentTimeMillis()
        val feedId = feedDao.insert(
            FeedEntity(
                url = url,
                title = parsed.title,
                createdAt = now,
                groupName = groupName.ifBlank { DEFAULT_GROUP },
                sourceType = sourceType,
                contentType = FeedContentTypeGuesser.guess(url, parsed.title),
            ),
        )
        val resolvedFeedId = feedId.takeIf { it != -1L } ?: feedDao.findIdByUrl(url) ?: return@withContext AddFeedResult.Duplicate

        engine.persistArticles(resolvedFeedId, parsed.articles, now)
        engine.backfillIcon(resolvedFeedId, parsed.siteUrl)
        AddFeedResult.Success
    }

    /**
     * OPML 盲导（ADR-0004）：解析 [stream] 后直接入库，不联网校验。
     * 标题取 OPML text/title，分组取 outline 嵌套路径；重复（规范化 URL 精确匹配）跳过计数。
     * 根元素非 OPML 时抛 [IllegalArgumentException]，由调用方转为提示。
     */
    suspend fun importOpml(stream: InputStream): OpmlImportResult = withContext(ioDispatcher) {
        val entries = OpmlParser.parse(stream)
        var imported = 0
        var skipped = 0
        val newIds = mutableListOf<Long>()
        val now = System.currentTimeMillis()
        entries.forEach { entry ->
            val url = normalizeHttpUrl(entry.xmlUrl) ?: run { skipped++; return@forEach }
            if (feedDao.findIdByUrl(url) != null) { skipped++; return@forEach }
            val feedId = feedDao.insert(
                FeedEntity(
                    url = url,
                    title = entry.title,
                    createdAt = now,
                    groupName = entry.group.ifBlank { DEFAULT_GROUP },
                    sourceType = FeedEntity.SOURCE_TYPE_RSS,
                    contentType = FeedContentTypeGuesser.guess(url, entry.title),
                ),
            )
            val resolved = feedId.takeIf { it != -1L } ?: feedDao.findIdByUrl(url)
            if (resolved == null) { skipped++; return@forEach }
            imported++
            newIds += resolved
        }
        OpmlImportResult(
            imported = imported,
            skipped = skipped,
            newFeedIds = newIds,
            groups = entries.map { it.group }.filter { it.isNotBlank() }.toSet(),
        )
    }

    /**
     * OPML 导出（#4）：把全部订阅源按分组序列化成 OPML 文本。
     * 分组即 OPML 文件夹（`技术/后端` 会被 [OpmlWriter] 还原成嵌套 outline）。
     * 库里没有站点主页字段（FeedEntity 无 siteUrl），HTML 链接属性留空——不捏造。
     * 这是导入的逆操作：用户的订阅清单不被本应用绑架。
     */
    suspend fun exportOpml(): String = withContext(ioDispatcher) {
        val entries = feedDao.getAll().map { feed ->
            OpmlEntry(
                group = feed.groupName,
                title = feed.title,
                xmlUrl = feed.url,
            )
        }
        OpmlWriter.write(entries)
    }
}
