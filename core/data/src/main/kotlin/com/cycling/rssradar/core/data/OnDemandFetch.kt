package com.cycling.rssradar.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ContentFetchLogDao
import com.cycling.rssradar.core.data.db.ContentFetchLogEntity
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FetchHostStat
import com.cycling.rssradar.core.data.parser.ArticleExtractor
import com.cycling.rssradar.core.data.parser.ContentFetcher
import com.cycling.rssradar.core.data.parser.ExtractionIssue
import com.cycling.rssradar.core.data.parser.FetchFailure
import com.cycling.rssradar.core.data.parser.FetchLogger
import com.cycling.rssradar.core.data.parser.FetchOutcome

/**
 * 一次按需抓取的结果。
 *
 * 存在理由：抓取失败是常态（反爬 / JS 渲染 / 付费墙），但「常态」不等于「可以不说」。
 * 旧契约返回 Boolean，调用方用 `runCatching {}` 一包就把原因丢了——读者只看到
 * 「正在获取全文…」然后什么都没有，失败与「这篇本来就没有正文」混为一谈，也无从重试。
 * ReadYou 的做法是让阅读页有一个 `Error(message)` 态；这里把原因结构化，
 * 由 UI 层取 [FetchFailure.label] / [ExtractionIssue.label] 说人话。
 */
sealed interface OnDemandResult {

    /** 已有够格正文，未联网。**不是失败**。 */
    data object AlreadyUsable : OnDemandResult

    /** 该订阅源关闭了全文抓取（CONTEXT.md：全文抓取开关）。不联网，但必须告知原因。 */
    data object FeedDisabled : OnDemandResult

    /** 文章不存在（可能刚被归档清理）。 */
    data object Missing : OnDemandResult

    /** 抓到并已写入。incomplete = 没过完整性校验，issue 说明是哪一类不完整。 */
    data class Fetched(
        val chars: Int,
        val incomplete: Boolean,
        val issue: ExtractionIssue?,
    ) : OnDemandResult

    /** 抓到了但比现有内容短 → 按「不把正文越抓越少」放弃写入。 */
    data class ShorterThanExisting(val got: Int, val existing: Int) : OnDemandResult

    /** 一个字都没抓到：kind 即原因。 */
    data class Failed(val kind: FetchFailure) : OnDemandResult
}

/**
 * **按需抓取**（On-demand fetch）模块：只在读者打开某篇文章时才去原网页取正文，
 * 连同**抓取日志**（Fetch log）一起收在这里（CONTEXT.md 两个有名字的概念）。
 *
 * 三条写入规则是这个模块存在的理由，此前它们住在 FeedRepository 这个
 * 530 行 / 50 个公开成员的抽屉里，与 markRead / renameFeed 同处一室：
 * 1. **够格的不重抓**——已有「够格」正文（feed 自带全文或之前抓过）就不联网；
 *    摘要级内容不算够格（旧实现只要 content != null 就跳过，于是摘要型 feed 永远抓不到原文）。
 * 2. **抓短了不覆盖**——抓出来的正文不比现有内容长就宁可不写，不把全文越抓越少。
 * 3. **每次抓取都留痕**——成功、不完整、失败三种 outcome 都写日志，只是 ok/issue 字段不同。
 *    一个字都抓不到时不写正文，记为提取失败。
 *
 * interface 只有五个方法加一条抓取缝；[ContentFetcher] / ArticleExtractor / [FetchLogger]
 * 是它的实现，不是调用方的助手。诊断页（「我的 → 正文抓取 → 全文抓取诊断」）是抓取日志的
 * 唯一读取方，直连本模块，不再经过 FeedRepository。
 */
class OnDemandFetch(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val contentFetchLogDao: ContentFetchLogDao,
    /**
     * 抓取缝：suspend 函数注入（与 [AutoSync] 同模式）。[ContentFetcher] 是 final 类
     * 无法 fake，这里只认「链接 → 抓取结果」，生产绑 `ContentFetcher::fetch`，
     * 测试塞内存实现就能断言三条写入规则。
     */
    private val fetchOutcome: suspend (link: String) -> FetchOutcome,
    /** 抓取侧的日志出口：正文不完整/放弃抓取的警告由它输出（诊断页与 logcat 同源）。 */
    private val logger: FetchLogger? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * 抓取一篇文章的原网页正文并入库。返回是否拿到了可用正文。
     *
     * 订阅源级预设（全文抓取开关）关闭时静默跳过：详情页只显示订阅源自带内容。
     * 失败是常态（反爬/JS 页），调用方静默降级即可，不必弹错误。
     */
    suspend fun fetch(articleId: Long): Boolean = when (fetchWithResult(articleId)) {
        is OnDemandResult.AlreadyUsable, is OnDemandResult.Fetched -> true
        else -> false
    }

    /**
     * 同 [fetch]，但把结果说清楚：够格的不重抓、源关闭、抓短了、失败了，
     * 各自有各自的返回值，调用方据此决定给读者看什么（失败原因可见、可重试）。
     */
    suspend fun fetchWithResult(articleId: Long): OnDemandResult = withContext(ioDispatcher) {
        val item = articleDao.getWithFeed(articleId) ?: return@withContext OnDemandResult.Missing
        // Feed 级预设（issue #9）：该源关闭全文抓取时不联网，降级到摘要（但原因要说出来）
        val feed = feedDao.getById(item.article.feedId)
        if (feed != null && !feed.fullContentEnabled) return@withContext OnDemandResult.FeedDisabled
        if (ContentQualification.hasUsableContent(item.article)) return@withContext OnDemandResult.AlreadyUsable

        val link = item.article.link
        val outcome = fetchOutcome(link)
        contentFetchLogDao.insert(outcome.toLog(link))
        when (outcome) {
            is FetchOutcome.Failure -> OnDemandResult.Failed(outcome.kind)
            is FetchOutcome.Success -> {
                val content = outcome.content
                val existingLength = item.article.contentText?.length ?: 0
                if (content.contentText.length <= existingLength) {
                    // 抓出来的比现有内容还短：宁可不写，也不把正文越抓越少
                    logger?.log(
                        FetchLogger.Level.WARN,
                        "抓取结果比现有内容短，放弃写入 chars=${content.contentText.length} " +
                            "existing=$existingLength host=${outcome.report.host} url=$link",
                    )
                    return@withContext OnDemandResult.ShorterThanExisting(
                        got = content.contentText.length,
                        existing = existingLength,
                    )
                }
                // 正文里与文章标题重复的标题行：头部已经显示过标题，正文里那段是多余的
                val html = ArticleExtractor.dropDuplicateTitle(content.contentHtml, item.article.title)
                articleDao.updateFetchedContent(
                    id = articleId,
                    content = html,
                    contentText = content.contentText,
                    contentSource = ArticleEntity.CONTENT_SOURCE_WEB,
                    readingMinutes = estimateReadingMinutes(content.contentText),
                    coverUrl = content.coverUrl,
                    contentIncomplete = !content.isComplete,
                )
                OnDemandResult.Fetched(
                    chars = content.contentText.length,
                    incomplete = !content.isComplete,
                    issue = content.issue,
                )
            }
        }
    }

    /** 诊断清单（ADR-0012）：有问题的抓取记录（失败或不完整），诊断页直接展示。 */
    fun observeProblems(limit: Int = 200): Flow<List<ContentFetchLogEntity>> =
        contentFetchLogDao.observeProblems(limit)

    /** 按站点聚合的失败/不完整统计。 */
    fun observeHostStats(): Flow<List<FetchHostStat>> = contentFetchLogDao.observeHostStats()

    /** 清空诊断记录（用户手动重置）。 */
    suspend fun clearLogs() = contentFetchLogDao.clear()

    /** 单篇的抓取历史（看重试与状态码演变）。 */
    suspend fun historyOf(link: String): List<ContentFetchLogEntity> =
        contentFetchLogDao.historyOf(link, limit = 5)

    // 读侧判定收敛到 ContentQualification（「够格」规则唯一落点），本类不再自持实现。

    /** 抓取结果 → 诊断记录。失败与「成功但不完整」都留痕，只是 ok/issue 字段不同。 */
    private fun FetchOutcome.toLog(link: String): ContentFetchLogEntity = when (this) {
        is FetchOutcome.Success -> ContentFetchLogEntity(
            link = link,
            host = report.host,
            statusCode = report.statusCode,
            attempts = report.attempts,
            pages = report.pages,
            ok = true,
            failure = null,
            issue = content.issue.name,
            contentChars = report.contentChars,
            durationMs = report.durationMs,
            createdAt = System.currentTimeMillis(),
        )
        is FetchOutcome.Failure -> ContentFetchLogEntity(
            link = link,
            host = report.host,
            statusCode = report.statusCode,
            attempts = report.attempts,
            pages = 0,
            ok = false,
            failure = kind.name,
            issue = null,
            contentChars = 0,
            durationMs = report.durationMs,
            createdAt = System.currentTimeMillis(),
        )
    }
}
