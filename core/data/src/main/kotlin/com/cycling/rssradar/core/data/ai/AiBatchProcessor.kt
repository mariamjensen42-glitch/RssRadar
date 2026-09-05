package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.AiSupportDao
import com.cycling.rssradar.core.data.db.AiTaskEntity
import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.domain.ai.AiReadingStats
import com.cycling.rssradar.core.data.store.AiBudgetStore
import com.cycling.rssradar.core.data.store.AiFeatureStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit


/** 一轮批处理的结果，通知文案与队列页诊断都用它。 */
data class AiBatchReport(
    var succeeded: Int = 0,
    var failed: Int = 0,
    var skipped: Int = 0,
    var outOfBudget: Boolean = false,
) {
    val processed: Int get() = succeeded + failed
    fun isEmpty(): Boolean = processed == 0 && skipped == 0
}


/**
 * AI 批处理的编排：排程入队 + 领取执行。
 *
 * **不依赖 WorkManager**，是纯协程逻辑——后台调度只是它的一个调用者，
 * 手动点"立即跑一批"也能调。这样批处理逻辑可以在 JVM 上单测，
 * 而 WorkManager 那层薄到不需要测。
 *
 * 单轮任务量刻意有上限（[MAX_CLAIM]）：一次领太多，进程被杀时已完成的就白做了，
 * 而且任务是在**领取时刻**按当时的开关与预算决定的，领一大把会让后续的开关变更迟迟不生效。
 */
class AiBatchProcessor(
    private val queue: AiTaskQueue,
    private val runner: AiFeatureRunner,
    private val supportDao: AiSupportDao,
    private val profileDao: FeedAiProfileDao,
    private val feedDao: FeedDao,
    private val artifacts: AiArtifactRepository,
    private val budget: AiBudgetStore,
    private val featureStore: AiFeatureStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * 排一次日常批处理：把近期文章上"已开启但还没产物"的功能排进队列。
     * @return 本次新入队的任务数。
     */
    suspend fun scheduleDaily(
        windowMs: Long = DEFAULT_WINDOW_MS,
        candidateLimit: Int = DEFAULT_CANDIDATE_LIMIT,
    ): Int {
        queue.maintenance(clock())

        val enabled = featureStore.state.value.enabled
        val articleFeatures = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.ARTICLE }
        val feedFeatures = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.FEED }
        val globalFeatures = AiFeature.BATCH_FEATURES
            .filter { it in enabled && it.scope == AiScope.GLOBAL }
        if (articleFeatures.isEmpty() && feedFeatures.isEmpty() && globalFeatures.isEmpty()) return 0

        val now = clock()
        val candidates = if (articleFeatures.isEmpty()) emptyList() else {
            supportDao.processableIdsSince(now - windowMs, candidateLimit)
        }
        val feedIds = if (feedFeatures.isEmpty()) emptyList() else {
            feedDao.getAll().map { it.id }
        }

        val profiles = profileDao.getAll().associate { it.feedId to it.priority }
        val articleFeed = if (candidates.isEmpty()) emptyMap() else {
            supportDao.briefsOf(candidates).associate { it.id to it.feedId }
        }
        val remaining = budget.current().remainingToday

        val specs = AiTaskPlanner.planAll(
            enabled = enabled,
            articleIds = candidates,
            feedIds = feedIds,
            existingKeys = artifacts.existingKeys(candidates),
            remainingBudget = remaining,
            articlePriorityOf = { articleFeed[it]?.let { f -> profiles[f] } ?: 0 },
            feedPriorityOf = { profiles[it] ?: 0 },
        )
        return queue.enqueue(specs, now)
    }

    /**
     * 消化队列：领一批任务并发执行，直到额度用尽或队列清空。
     *
     * 并发度取自预算设置（默认 2）。用 Semaphore 而不是给每个任务 launch 一个协程——
     * 并发上限是给用户省钱、给服务端减压的，必须是个硬闸。
     */
    suspend fun drain(maxTasks: Int = DEFAULT_DRAIN_LIMIT): AiBatchReport = coroutineScope {
        val now = clock()
        val tasks = queue.claim(now, maxTasks.coerceIn(1, MAX_CLAIM))
        if (tasks.isEmpty()) return@coroutineScope AiBatchReport()

        val concurrency = budget.current().concurrentLimit
        val gate = Semaphore(concurrency)
        val report = AiBatchReport()
        val lock = Mutex()

        tasks.map { task ->
            async {
                gate.withPermit {
                    val single = processOne(task)
                    lock.withLock { merge(report, single) }
                }
            }
        }.awaitAll()

        report
    }

    /** 一次完整的日常批处理：先排程再消化。Worker 调这个。 */
    suspend fun runDaily(maxTasks: Int = DEFAULT_DRAIN_LIMIT): AiBatchReport {
        scheduleDaily()
        return drain(maxTasks)
    }

    // ── 单任务执行 ──────────────────────────────────────────────────────────

    private suspend fun processOne(task: AiTaskEntity): AiBatchReport {
        val feature = AiFeature.fromDbValue(task.kind)
        if (feature == null) {
            // 未知 kind：老版本残留或未来功能。直接作废而不是重试——重试一万次也认不出来。
            queue.delete(task.id)
            return AiBatchReport(skipped = 1)
        }

        val attempts = task.attempts + 1
        queue.markRunning(task, attempts, clock())

        val outcome = when {
            // 跨文章与统计类功能的上下文要现组，runner 自己不知道该取哪批文章、哪些数字。
            feature in CROSS_ARTICLE -> {
                val context = crossArticleContext(feature, task.targetId)
                    ?: return abort(task, attempts, "没有可用于${feature.label}的内容")
                runner.runWithContext(feature, task.targetId, context)
            }

            feature in STATS_SCOPED -> {
                val context = statsContext(feature, task.targetId)
                    ?: return abort(task, attempts, "统计数据不足，跳过${feature.label}")
                runner.runWithContext(feature, task.targetId, context)
            }

            else -> runner.run(feature, task.targetId)
        }

        return when (outcome) {
            is AiFeatureRunner.Outcome.Success -> {
                queue.succeed(task, attempts, clock())
                AiBatchReport(succeeded = 1)
            }

            AiFeatureRunner.Outcome.OutOfBudget -> {
                // 额度用尽：推迟到一小时后再试，**绝不记失败**——
                // 记失败会触发退避重试，重试又撞额度，一个小时内能把任务耗到终态。
                queue.postpone(task.id, clock() + RETRY_AFTER_BUDGET_MS, clock())
                AiBatchReport(outOfBudget = true)
            }

            is AiFeatureRunner.Outcome.Skipped -> {
                // 功能被关掉了：任务作废，留着只会每天重试一次然后继续跳过。
                queue.delete(task.id)
                AiBatchReport(skipped = 1)
            }

            is AiFeatureRunner.Outcome.Failed -> {
                val retried = queue.fail(task, attempts, outcome.message, clock())
                if (retried) AiBatchReport(skipped = 1) else AiBatchReport(failed = 1)
            }
        }
    }

    private suspend fun abort(task: AiTaskEntity, attempts: Int, message: String): AiBatchReport {
        val retried = queue.fail(task, attempts, message, clock())
        return if (retried) AiBatchReport(skipped = 1) else AiBatchReport(failed = 1)
    }

    // ── 上下文组装 ──────────────────────────────────────────────────────────

    /**
     * 跨文章功能：把近期候选文章作为列表送进去，焦点文章附上正文。
     * 候选统一走 [AiSupportDao.processableIdsSince]，保证批处理与手动触发看到的是同一批。
     */
    private suspend fun crossArticleContext(feature: AiFeature, targetId: Long): AiPromptContext? {
        val now = clock()
        val ids = when (feature) {
            AiFeature.DAILY_BRIEF -> {
                val todayStart = now - DEFAULT_WINDOW_MS
                supportDao.processableIdsSince(todayStart, CROSS_LIMIT)
            }

            AiFeature.DISCOVER -> supportDao.unreadIdsSince(now - DEFAULT_WINDOW_MS, CROSS_LIMIT)
            else -> supportDao.processableIdsSince(now - DEFAULT_WINDOW_MS, CROSS_LIMIT)
        }
        if (ids.isEmpty()) return null

        val ordered = if (targetId in ids) {
            listOf(targetId) + ids.filter { it != targetId }
        } else {
            ids
        }
        val companions = runner.briefsOf(ordered.take(CROSS_LIMIT))
        if (companions.isEmpty()) return null

        val focus = companions.firstOrNull { it.id == targetId }
        // 简报与发现只按标题挑文章（几十篇正文塞不进上下文）；
        // 去重、聚合、事件合并必须给正文，否则模型只能靠标题猜是不是同一件事。
        val body = if (focus != null && feature !in TITLE_ONLY) {
            runner.bodyOf(targetId).orEmpty()
        } else {
            ""
        }

        return AiPromptContext(
            title = focus?.title.orEmpty(),
            feedTitle = focus?.feedTitle.orEmpty(),
            body = body,
            companions = companions,
        )
    }

    /**
     * 统计类功能：**数字全部来自数据库真实统计**，模型只负责把数字组织成人话。
     *
     * 这条边界不能让给模型——让它"估算"阅读时长分布，产出的就是一份看起来很专业、
     * 但每个数字都对不上账的假报告。
     */
    private suspend fun statsContext(feature: AiFeature, targetId: Long): AiPromptContext? {
        val now = clock()
        return when (feature) {
            AiFeature.HABIT -> {
                val since = now - HABIT_WINDOW_MS
                val times = supportDao.openTimesSince(since)
                if (times.isEmpty()) return null
                val counts = supportDao.openCountsByFeedSince(since)
                val feedTitles = feedDao.getAll().associate { it.id to it.title }
                val hours = AiReadingStats.activeHours(times, zoneOffset(now))
                val concentration = AiReadingStats.concentration(counts.map { it.total })
                AiPromptContext(
                    title = "阅读习惯分析",
                    feedTitle = "本地统计",
                    extra = buildString {
                        appendLine("统计区间：最近 ${HABIT_WINDOW_MS / 86_400_000} 天")
                        appendLine("打开文章次数：${times.size}")
                        appendLine("活跃时段（小时）：${hours.joinToString("、")}")
                        appendLine("订阅源集中度：${format2(concentration)}（0=完全分散，1=全部集中在一个源）")
                        appendLine("按订阅源的打开次数：")
                        counts.take(10).forEach {
                            appendLine("- ${feedTitles[it.feedId] ?: "已删除的源"}：${it.total} 次")
                        }
                    },
                )
            }

            AiFeature.DAILY_REPORT -> {
                val since = now - DAY_MS
                val read = supportDao.countReadSince(since)
                val unread = supportDao.unreadIdsSince(since, CROSS_LIMIT)
                if (read == 0 && unread.isEmpty()) return null
                val companions = runner.briefsOf(unread)
                AiPromptContext(
                    title = "每日阅读报告",
                    feedTitle = "本地统计",
                    extra = buildString {
                        appendLine("统计区间：最近 24 小时")
                        appendLine("已读文章数：$read")
                        appendLine("未处理文章数：${unread.size}")
                        appendLine("未处理文章清单见下方文章列表。")
                    },
                    companions = companions,
                )
            }

            AiFeature.FEED_HEALTH -> {
                val since = now - HEALTH_WINDOW_MS
                val feed = feedDao.getById(targetId) ?: return null
                val recent = supportDao.countRecentOfFeed(targetId, since)
                val total = supportDao.countOfFeed(targetId)
                val incomplete = supportDao.countIncompleteOfFeed(targetId)
                val lastFetched = supportDao.lastFetchedOfFeed(targetId)
                val incompleteRate = if (total == 0) 0.0 else incomplete.toDouble() / total
                AiPromptContext(
                    title = feed.title,
                    feedTitle = feed.title,
                    extra = buildString {
                        appendLine("订阅源：${feed.title}")
                        appendLine("订阅地址：${feed.url}")
                        appendLine("历史文章总数：$total")
                        appendLine("最近 30 天新增文章数：$recent")
                        appendLine("最后一次抓到文章：${lastFetched?.let { "${(now - it) / 86_400_000} 天前" } ?: "从未"}")
                        appendLine("正文不完整文章数：$incomplete")
                        appendLine("正文不完整比例：${formatPercent(incompleteRate)}")
                    },
                )
            }

            AiFeature.INTEREST_RANK, AiFeature.BUBBLE_BREAK, AiFeature.FEED_RECOMMEND -> {
                val feeds = feedDao.getAll()
                if (feeds.isEmpty()) return null
                val since = now - HABIT_WINDOW_MS
                val counts = supportDao.openCountsByFeedSince(since)
                val titles = feeds.associate { it.id to it.title }
                AiPromptContext(
                    title = feature.label,
                    feedTitle = "本地统计",
                    extra = buildString {
                        appendLine("已订阅来源（共 ${feeds.size} 个）：")
                        feeds.forEach { appendLine("- ${it.title}（${it.groupName}）") }
                        appendLine()
                        appendLine("最近 30 天按来源的打开次数：")
                        counts.take(20).forEach {
                            appendLine("- ${titles[it.feedId] ?: "已删除的源"}：${it.total} 次")
                        }
                    },
                )
            }

            AiFeature.COLD_START -> AiPromptContext(
                title = "兴趣冷启动",
                feedTitle = "本地统计",
                extra = feedDao.getAll().joinToString("\n") { "- ${it.title}" },
            )

            else -> null
        }
    }

    private fun zoneOffset(millis: Long): Int = java.util.TimeZone.getDefault().getOffset(millis)

    // 固定 Locale：默认 Locale 随系统语言变化，某些语言下小数点会变成逗号，
    // 拼进 prompt 的数字格式一变，模型的解析行为也可能跟着变。
    private fun format2(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun formatPercent(value: Double): String = String.format(java.util.Locale.US, "%.0f%%", value * 100)

    private fun merge(target: AiBatchReport, delta: AiBatchReport) {
        target.succeeded += delta.succeeded
        target.failed += delta.failed
        target.skipped += delta.skipped
        target.outOfBudget = target.outOfBudget || delta.outOfBudget
    }

    companion object {
        /** 候选窗口：只处理最近 3 天抓到的文章——再老的文章补分析没有意义。 */
        const val DEFAULT_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L

        const val DEFAULT_CANDIDATE_LIMIT = 300

        /** 单轮最多消化多少任务。受 WorkManager 10 分钟限制与日预算双重约束。 */
        const val DEFAULT_DRAIN_LIMIT = 60

        /** 单次领取上限，防止一次性领太多在进程被杀后白做。 */
        const val MAX_CLAIM = 60

        /** 跨文章功能一次最多送多少篇进 prompt——再多会撑爆上下文且显著变慢。 */
        const val CROSS_LIMIT = 40

        const val HABIT_WINDOW_MS = 30 * 24 * 60 * 60 * 1000L
        const val HEALTH_WINDOW_MS = 30 * 24 * 60 * 60 * 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** 额度用尽后推迟多久再试（等到次日零点附近）。 */
        const val RETRY_AFTER_BUDGET_MS = 60 * 60 * 1000L

        private val CROSS_ARTICLE = setOf(
            AiFeature.DEDUPE,
            AiFeature.AGGREGATE,
            AiFeature.EVENT_MERGE,
            AiFeature.DAILY_BRIEF,
            AiFeature.DISCOVER,
        )

        /** 只按标题挑文章的跨文章功能——几十篇正文塞不进上下文，也没必要。 */
        private val TITLE_ONLY = setOf(AiFeature.DAILY_BRIEF, AiFeature.DISCOVER)

        private val STATS_SCOPED = setOf(
            AiFeature.HABIT,
            AiFeature.DAILY_REPORT,
            AiFeature.FEED_HEALTH,
            AiFeature.INTEREST_RANK,
            AiFeature.BUBBLE_BREAK,
            AiFeature.FEED_RECOMMEND,
            AiFeature.COLD_START,
        )
    }
}
