package com.cycling.rssradar.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.domain.concurrency.quietCatching
import com.cycling.rssradar.core.data.qualify.ContentQualification
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.data.repository.FeedRepository
import com.cycling.rssradar.core.data.service.OnDemandFetch
import com.cycling.rssradar.core.data.service.OnDemandResult
import com.cycling.rssradar.core.data.recommend.Recommendation
import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure
import com.cycling.rssradar.core.data.ai.AiArtifactRepository
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.data.ai.AiFeatureRunner
import com.cycling.rssradar.core.data.ai.AiFeatureSpecs
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.core.model.AiFeatureSettings
import com.cycling.rssradar.core.data.store.prefs.AiFeatureStore
import com.cycling.rssradar.core.data.store.prefs.AiStore
import com.cycling.rssradar.core.model.LinkShareState
import com.cycling.rssradar.core.data.annotation.AnnotationRepository
import com.cycling.rssradar.core.data.db.ArticleAnnotationEntity
import com.cycling.rssradar.core.data.store.prefs.LinkStore
import com.cycling.rssradar.core.model.ReadingPrefs
import com.cycling.rssradar.core.data.store.prefs.ReadingPositionStore
import com.cycling.rssradar.core.data.store.prefs.ReadingPrefsStore
import com.cycling.rssradar.core.ui.mvi.MviStateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * 按需抓取在阅读页的可见状态（对齐 ReadYou 的 ReaderState：Loading / FullContent / Error）。
 *
 * 此前抓取结果是一个被 `runCatching` 吞掉的 Boolean：读者只看到「正在获取全文…」然后
 * 什么都没有——不知道失败原因，也没有重试入口。这里把每种结果都摊开，由 UI 决定显示什么：
 * **可能失败的交互必须让原因可见，且必须能重试**（UI 铁律）。
 */
sealed interface ContentFetchState {
    /** 尚未抓取。 */
    data object Idle : ContentFetchState

    /** 正在抓取。 */
    data object Loading : ContentFetchState

    /** 已有够格正文（自带或已抓到）：UI 不显示任何抓取提示。 */
    data object Ready : ContentFetchState

    /** 抓到了但没过完整性校验：issue 说明是哪一类不完整。 */
    data class Incomplete(val issue: ExtractionIssue?) : ContentFetchState

    /** 没抓到：reason 是给读者看的中文原因（不是日志里的枚举名）。 */
    data class Failed(val reason: FetchFailReason) : ContentFetchState
}

/** 文章详情事件。load 为生命周期，留 init，不进 Intent。 */
sealed interface ArticleDetailIntent {
    data object ToggleStarred : ArticleDetailIntent
    data object ToggleBookmarked : ArticleDetailIntent
    /** 生成/重新生成 AI 摘要（结果持久化在 articles.aiSummary）。 */
    data object GenerateSummary : ArticleDetailIntent
    /** 翻译开/关：未显示译文时发起翻译，显示中则切回原文。 */
    data object ToggleTranslation : ArticleDetailIntent
    /** 清缓存重译（issue #44：会话缓存 + 明确的重译按钮）。 */
    data object RetranslateArticle : ArticleDetailIntent

    /** 重新抓取正文：抓取失败后读者点一下重试（ReadYou 的 Error 态重试同款）。 */
    data object RetryFetch : ArticleDetailIntent

    /**
     * 正文源切换：true = 看订阅源摘要（ReadYou 的 renderDescriptionContent），
     * false = 看正文。只作用于当前这一篇，换文章即复位。
     */
    data class SetPreferSummary(val preferSummary: Boolean) : ArticleDetailIntent

    /** 手动跑一项文章级 AI 分析（AI 智能功能模块）。 */
    data class RunAi(val feature: AiFeature) : ArticleDetailIntent

    /** 向文章提问（AI 智能功能模块 · 文章问答）。question 为空时忽略。 */
    data class AskArticle(val question: String) : ArticleDetailIntent

    /** 解释文中术语（AI 智能功能模块 · 划词解释）。term 为空时忽略。 */
    data class ExplainTerm(val term: String) : ArticleDetailIntent

    data object ConsumeAiMessage : ArticleDetailIntent
}

/**
 * 面板上以**按钮**触发的功能：不需要额外输入，点一下就跑。
 *
 * 只列**在单篇文章上有意义**的那些——全局功能（每日简报、阅读习惯）和订阅源级功能
 * （健康监控）在文章页触发没有落点，列出来只会让用户点出一个莫名其妙的结果。
 *
 * **顺序 = 面板上的分组顺序**（读懂 / 分享，见 `AI_BUTTON_GROUPS`）：
 * 多个按钮平铺时用户得逐个读完才发现自己要哪个，按用途分组后才谈得上"扫一眼就找到"。
 * 同一个顺序也决定产物卡片的排列，按钮与结果一一对应。
 */
val ARTICLE_AI_BUTTONS: List<AiFeature> = listOf(
    AiFeature.OUTLINE,
    AiFeature.NOISE,
    AiFeature.KEYWORDS,
    AiFeature.SHARE_COPY,
)

/**
 * 面板里会**展示产物**的功能：[ARTICLE_AI_BUTTONS] 加上两个需要输入的功能
 * （问答与划词解释）。
 *
 * 拆成两个清单是因为它们的触发方式不同：一个靠按钮，一个靠输入框。
 * 但产物都挂在文章上、都该在面板里看到，所以展示时合并。
 */
val ARTICLE_AI_FEATURES: List<AiFeature> =
    ARTICLE_AI_BUTTONS + listOf(AiFeature.QA, AiFeature.GLOSSARY)

/** 同源相邻文章 id（底栏上一篇/下一篇）。prev = 更早一篇，next = 更新一篇；列表尽头为 null。 */
data class ArticleNeighbors(val prevId: Long?, val nextId: Long?)

/**
 * 阅读页的渲染输入快照，唯一产出点是 [ArticleDetailViewModel.uiState]。
 * 定义在 VM 侧（而不是 Screen 侧）：状态是 VM 的契约，Screen 只是消费者。
 */
data class ArticleDetailUiState(
    val article: ArticleWithFeed? = null,
    val annotations: List<ArticleAnnotationEntity> = emptyList(),
    val initialLoadDone: Boolean = false,
    val isFetchingContent: Boolean = false,
    val contentFetch: ContentFetchState = ContentFetchState.Idle,
    val preferSummary: Boolean = false,
    val aiSummaryState: AiSummaryState = AiSummaryState.Idle,
    val translationState: TranslationState = TranslationState.None,
    val neighbors: ArticleNeighbors = ArticleNeighbors(null, null),
    val related: List<ArticleWithFeed> = emptyList(),
    val aiArtifacts: Map<Int, Any> = emptyMap(),
    val aiRunning: Set<Int> = emptySet(),
    val aiMessage: String? = null,
    val aiEnabledFeatures: AiFeatureSettings = AiFeatureSettings(),
    val aiKeyConfigured: Boolean = false,
    val readingPrefs: ReadingPrefs = ReadingPrefs(),
    val linkShare: LinkShareState = LinkShareState(),
)

/** AI 摘要生成状态。摘要本体在 article.aiSummary，这里只管过程。 */
sealed interface AiSummaryState {
    data object Idle : AiSummaryState
    data object Generating : AiSummaryState
    data class Failed(val message: String) : AiSummaryState
}

/**
 * 翻译显示状态（渐进式翻译，翻译功能 v2）。
 * - Progressing：分段翻译中，segments 随完成度增长；未完成段 translatedHtml = null，
 *   UI 渲染原文淡显——翻译进行中即可读已完成部分。
 * - Shown：全部分段完成。切回原文（None）不清缓存，再次 Toggle 缓存秒出。
 * - Failed：网络/Key 失败；进行到一半失败时已完成段不保留（下次重译）。
 */
data class TranslationSegmentUi(
    val originalHtml: String,
    val translatedHtml: String?,
    /**
     * 本段原文内的顶层块（双语对照的配对单位）。由 [com.cycling.rssradar.core.data.ai.TranslationSegments]
     * 分段时一次切好带过来——渲染侧不再把原文切第二遍，两级单位由同一个模块说了算。
     */
    val blocks: List<String>,
)

sealed interface TranslationState {
    data object None : TranslationState
    data class Progressing(val segments: List<TranslationSegmentUi>) : TranslationState {
        val doneCount: Int get() = segments.count { it.translatedHtml != null }
        val total: Int get() = segments.size
    }

    data class Shown(val segments: List<TranslationSegmentUi>) : TranslationState

    data class Failed(val message: String) : TranslationState
}

/** nav args 在 SavedStateHandle 里的键，与 ArticleDetailRoute 的参数名一致。 */
private const val KEY_ARTICLE_ID = "articleId"

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedRepository,
    /** 按需抓取原网页正文。直连模块，不经过 FeedRepository 转发。 */
    private val onDemandFetch: OnDemandFetch,
    private val readingPrefsStore: ReadingPrefsStore,
    private val linkStore: LinkStore,
    private val aiRepository: AiRepository,
    private val aiStore: AiStore,
    /** AI 智能功能模块：16 项功能的执行器与产物仓储。 */
    private val featureRunner: AiFeatureRunner,
    private val artifacts: AiArtifactRepository,
    private val featureStore: AiFeatureStore,
    /** 相关阅读（AiFeature.RELATED）：本地内容相似度，needsLlm=false。 */
    private val recommendation: Recommendation,
    /** 正文高亮与笔记（DB v17）。 */
    private val annotationRepository: AnnotationRepository,
    /** 每篇文章的阅读位置（比例）：重开长文回到上次读到的地方。 */
    private val readingPositionStore: ReadingPositionStore,
) : ViewModel(), MviStateViewModel<ArticleDetailIntent, ArticleDetailUiState> {

    /**
     * 阅读页的唯一可写状态容器。文章级字段（含 AI 过程状态）都写在这里，
     * 阅读偏好 / 分享偏好 / AI 功能开关三条 Store 流由 [uiState] 折叠进来。
     */
    private val _uiState = MutableStateFlow(ArticleDetailUiState())

    private var annotationsJob: Job? = null

    /** 当前文章 id，AI 操作的目标；load 时更新。 */
    private var currentArticleId: Long = -1L

    /** 连点上/下一篇时取消前一次未完成的加载，避免旧文章晚到覆盖新文章。 */
    private var loadJob: Job? = null

    private var translationJob: Job? = null

    /**
     * 阅读页的单一状态快照：本地状态 + 三条偏好 Store 流，UI 侧只订阅这一条。
     *
     * 原先 UI 一次 collect 17 个流（docs/perf/compose-performance.md P0-2），
     * 任一发射都会触发整页作用域重组；合成后订阅点只剩一个，重组扇出随之收敛。
     */
    override val uiState: StateFlow<ArticleDetailUiState> = combine(
        _uiState,
        readingPrefsStore.state,
        linkStore.state,
        featureStore.state,
    ) { state, prefs, link, features ->
        state.copy(readingPrefs = prefs, linkShare = link, aiEnabledFeatures = features)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value)

    init {
        // articleId 来自 nav args（类型安全路由写入 SavedStateHandle，issue #32）。
        // 进程重建时 NavController 恢复 back stack 并重放 args，本 init 重新触发 → 当前文章不丢。
        // 兼容 Long / String 两种存法（Navigation 版本对 bundle 内类型处理有差异）。
        val articleId = savedStateHandle.get<Long>(KEY_ARTICLE_ID)
            ?: savedStateHandle.get<String>(KEY_ARTICLE_ID)?.toLongOrNull()
        if (articleId != null) load(articleId)
    }

    /** 上次读到的位置（比例）。null = 没有值得恢复的记录：没读过，或读过又滚回了开头。 */
    fun readingRatio(articleId: Long): Float? = readingPositionStore.get(articleId)

    /**
     * 记下当前阅读位置。比例落在「还在开头 / 已经读完」区间时 Store 会把记录清掉——
     * 那代表新的语义（回到开头），不该留着上一次的中间位置。
     */
    fun rememberReadingRatio(articleId: Long, ratio: Float) {
        readingPositionStore.save(articleId, ratio)
    }

    /** 阅读偏好属 UI 环境而非业务事件，按「纯函数与状态 producer 保持 fun」的先例走普通方法，不进 Intent 面。 */
    fun updateReadingPrefs(transform: (ReadingPrefs) -> ReadingPrefs) {
        readingPrefsStore.update(transform)
    }

    /** 幂等：init 自加载后，screen 生命周期处重复调用只是重查 DB。切换文章时重置 AI 过程状态。 */
    fun load(articleId: Long) {
        val isNewArticle = articleId != currentArticleId
        currentArticleId = articleId
        if (isNewArticle) {
            annotationsJob?.cancel()
            annotationsJob = viewModelScope.launch {
                annotationRepository.observeOfArticle(articleId).collect { list ->
                    _uiState.update { it.copy(annotations = list) }
                }
            }
            // 译文/摘要缓存都按文章 id 键控，换文章必须清过程状态并取消进行中的翻译，
            // 否则旧译文/旧进度会顶在新文章上
            translationJob?.cancel()
            translationJob = null
            _uiState.update {
                it.copy(
                    translationState = TranslationState.None,
                    aiSummaryState = AiSummaryState.Idle,
                    aiArtifacts = emptyMap(),
                    aiRunning = emptySet(),
                    aiMessage = null,
                    // 相关阅读随文章换：先清空避免上一篇文章的相关推荐闪现在新文章下
                    related = emptyList(),
                    // 抓取状态随文章换：上一篇的「抓取失败」提示不能贴在新文章上
                    contentFetch = ContentFetchState.Idle,
                    // 摘要模式是单篇决定：上一篇「切回摘要」的选择不能带到下一篇
                    preferSummary = false,
                )
            }
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = repository.getArticle(articleId)
            _uiState.update { it.copy(article = loaded, initialLoadDone = true) }
            val neighbors = loadNeighbors(articleId)
            _uiState.update { it.copy(neighbors = neighbors) }
            // 相邻预取在 loadJob 之外：它是顺手活，不该被下一次 load 取消，
            // 也不该拖慢当前文章正文的加载（与 ReadYou 预取相邻文章同一动机）。
            prefetchNeighbors(neighbors)
            if (_uiState.value.article?.article?.isRead == false) {
                repository.markRead(articleId)
            }
            // 推荐画像的采集点：每次打开都记，源亲和度的时间衰减靠它。
            // 这一列不参与内容状态刷新，也不影响已读语义。
            repository.markOpened(articleId)
            fetchFullContentIfNeeded(articleId)
            loadAiArtifacts(articleId)
            loadRelated(articleId)
        }
        // 每次进文章都重读一次：用户可能刚在设置页填了 Key 就切回来。
        _uiState.update { it.copy(aiKeyConfigured = aiStore.hasKey()) }
    }

    /**
     * 相关阅读：功能开启才算（关了就不该白算，哪怕它是纯本地的）。
     * 结果回来时若用户已切到别的文章（currentArticleId 变了）则丢弃——
     * 慢半拍的旧结果盖在新文章上是典型的过期覆盖。
     */
    private suspend fun loadRelated(articleId: Long) {
        if (!featureStore.isEnabled(AiFeature.RELATED)) return
        val ids = recommendation.related(articleId)
        val items = recommendation.loadOrdered(ids)
        if (currentArticleId == articleId) _uiState.update { it.copy(related = items) }
    }

    /** 读本文已有的 AI 产物。与正文加载串行，避免同篇文章两个协程抢写状态。 */
    private suspend fun loadAiArtifacts(articleId: Long) {
        val loaded = HashMap<Int, Any>()
        ARTICLE_AI_FEATURES.forEach { feature ->
            val raw = artifacts.rawOf(feature, articleId) ?: return@forEach
            val parsed = runCatching { AiFeatureSpecs.parse(feature, raw) }.getOrNull() ?: return@forEach
            if (AiFeatureSpecs.isMeaningful(feature, parsed)) loaded[feature.dbValue] = parsed
        }
        _uiState.update { it.copy(aiArtifacts = loaded) }
    }

    private fun runAi(feature: AiFeature, question: String? = null) {
        val articleId = currentArticleId
        if (articleId < 0) return
        val key = feature.dbValue
        if (key in _uiState.value.aiRunning) return
        if (!featureStore.isEnabled(feature)) {
            _uiState.update { it.copy(aiMessage = "「${feature.label}」未开启，可在设置里打开") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(aiRunning = it.aiRunning + key) }
            try {
                when (val outcome = featureRunner.run(feature, articleId, question)) {
                    is AiFeatureRunner.Outcome.Success -> {
                        val parsed = runCatching { AiFeatureSpecs.parse(feature, outcome.payload) }.getOrNull()
                        // 界面状态只在用户还停在这篇时才写。这个协程不挂在 loadJob 下，
                        // 切篇不会取消它：慢半拍回来的旧结果会直接盖在新文章上
                        // （「AI 摘要是上一篇的」），是最典型的一类过期覆盖。
                        if (currentArticleId == articleId && parsed != null) {
                            _uiState.update { it.copy(aiArtifacts = it.aiArtifacts + (key to parsed)) }
                        }
                    }

                    is AiFeatureRunner.Outcome.Failed -> {
                        if (currentArticleId == articleId) {
                            _uiState.update { it.copy(aiMessage = outcome.message) }
                        }
                    }

                    AiFeatureRunner.Outcome.OutOfBudget -> {
                        if (currentArticleId == articleId) {
                            _uiState.update { it.copy(aiMessage = "今日 AI 额度已用尽，明天再试") }
                        }
                    }

                    is AiFeatureRunner.Outcome.Skipped -> {
                        if (currentArticleId == articleId) {
                            _uiState.update { it.copy(aiMessage = outcome.reason) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // 协程取消（切文章、退出页面）必须原样抛出，吞掉会破坏结构化并发。
                throw e
            } catch (e: Exception) {
                // 兜底：任何漏网的异常都要把 loading 收掉并给出原因。
                // 少这层 try，一次异常就会让按钮永久转圈，而用户无从得知发生了什么。
                if (currentArticleId == articleId) {
                    val text = "「${feature.label}」出错了：${e.message ?: e.javaClass.simpleName}"
                    _uiState.update { it.copy(aiMessage = text) }
                }
            } finally {
                // 切篇时 load() 已把 aiRunning 清空，此处再减会误伤新文章上同名功能的
                // 运行标记（按钮提前恢复可点），所以要判归属。
                if (currentArticleId == articleId) {
                    _uiState.update { it.copy(aiRunning = it.aiRunning - key) }
                }
            }
        }
    }

    /** 同源相邻文章：按信息流列表序（新→旧）取当前文章前后各一篇。 */
    private suspend fun loadNeighbors(articleId: Long): ArticleNeighbors {
        val current = _uiState.value.article ?: return ArticleNeighbors(null, null)
        val ids = repository.getFeedArticleIds(current.article.feedId)
        val index = ids.indexOf(articleId)
        if (index == -1) return ArticleNeighbors(null, null)
        return ArticleNeighbors(
            prevId = ids.getOrNull(index + 1), // 列表序更靠后 = 发布更早
            nextId = ids.getOrNull(index - 1), // 列表序更靠前 = 发布更新
        )
    }

    /**
     * 文章没有 feed 自带正文时按需抓原网页。
     *
     * 结果写进 [ArticleDetailUiState.contentFetch]：不再 `runCatching` 一吞了之——
     * 失败要给出中文原因，读者才知道该重试还是该去看原文。
     */
    private suspend fun fetchFullContentIfNeeded(articleId: Long) {
        val current = _uiState.value.article ?: return
        // 读侧「够格」判定唯一落点在 ContentQualification；够格就不联网。
        // 但库里已带「不完整」标记的，仍要把它显示出来——这次不抓不等于那标记不存在。
        if (ContentQualification.hasUsableContent(current.article)) {
            val state = if (current.article.contentIncomplete) {
                ContentFetchState.Incomplete(null)
            } else {
                ContentFetchState.Ready
            }
            _uiState.update { it.copy(contentFetch = state) }
            return
        }
        _uiState.update { it.copy(isFetchingContent = true, contentFetch = ContentFetchState.Loading) }
        val result = try {
            quietCatching { onDemandFetch.fetchWithResult(articleId) }
                .getOrElse { OnDemandResult.Failed(FetchFailure.NETWORK) }
        } finally {
            _uiState.update { it.copy(isFetchingContent = false) }
        }
        val state = result.toState()
        val refreshed = repository.getArticle(articleId)
        _uiState.update { it.copy(contentFetch = state, article = refreshed) }
    }

    /**
     * 相邻文章的正文预取：翻到上一篇/下一篇时正文已在库里，不用再等一次抓取。
     *
     * 只预取紧邻的两篇，且 [OnDemandFetch] 自己会跳过已有正文的、以及关闭了全文抓取的源。
     * **不做全量后台预抓**（ReadYou 的 ReaderWorker 会抓全部未读）：流量与电量不可控，
     * 也违背本项目「按需抓取」的约定。
     */
    private fun prefetchNeighbors(neighbors: ArticleNeighbors) {
        listOfNotNull(neighbors.prevId, neighbors.nextId).forEach { id ->
            viewModelScope.launch { runCatching { onDemandFetch.fetch(id) } }
        }
    }

    /** 抓取结果 → 阅读页可见状态。中文原因统一在这里产生，UI 只负责显示。 */
    private fun OnDemandResult.toState(): ContentFetchState = when (this) {
        is OnDemandResult.AlreadyUsable -> ContentFetchState.Ready
        is OnDemandResult.FeedDisabled ->
            ContentFetchState.Failed(FetchFailReason.FeedDisabled)

        is OnDemandResult.Missing -> ContentFetchState.Failed(FetchFailReason.ArticleMissing)
        is OnDemandResult.Fetched ->
            if (incomplete) ContentFetchState.Incomplete(issue) else ContentFetchState.Ready

        is OnDemandResult.ShorterThanExisting ->
            ContentFetchState.Failed(FetchFailReason.ShorterThanExisting(got, existing))

        is OnDemandResult.Failed -> ContentFetchState.Failed(FetchFailReason.FromFailure(kind))
    }

    /** 重新抓取正文（读者点重试）。正在抓取、或这篇已有正文时无动作。 */
    private fun retryFetch() {
        val id = currentArticleId
        if (id < 0) return
        if (_uiState.value.contentFetch is ContentFetchState.Loading) return
        viewModelScope.launch { fetchFullContentIfNeeded(id) }
    }

    override fun onIntent(intent: ArticleDetailIntent) {
        when (intent) {
            ArticleDetailIntent.ToggleStarred -> toggleStarred()
            ArticleDetailIntent.ToggleBookmarked -> toggleBookmarked()
            ArticleDetailIntent.GenerateSummary -> generateSummary()
            ArticleDetailIntent.ToggleTranslation -> toggleTranslation()
            ArticleDetailIntent.RetranslateArticle -> retranslate()
            ArticleDetailIntent.RetryFetch -> retryFetch()
            is ArticleDetailIntent.SetPreferSummary ->
                _uiState.update { it.copy(preferSummary = intent.preferSummary) }

            is ArticleDetailIntent.RunAi -> runAi(intent.feature)
            is ArticleDetailIntent.AskArticle ->
                if (intent.question.isNotBlank()) runAi(AiFeature.QA, intent.question.trim())

            is ArticleDetailIntent.ExplainTerm ->
                if (intent.term.isNotBlank()) runAi(AiFeature.GLOSSARY, intent.term.trim())

            ArticleDetailIntent.ConsumeAiMessage -> _uiState.update { it.copy(aiMessage = null) }
        }
    }

    /** 生成 AI 摘要。未配置 Key 直接失败并引导；结果入库后重查文章刷新卡片。 */
    private fun generateSummary() {
        if (_uiState.value.aiSummaryState is AiSummaryState.Generating) return
        if (!aiStore.hasKey()) {
            _uiState.update {
                it.copy(aiSummaryState = AiSummaryState.Failed("未配置 API Key，请到「我的」页设置 DeepSeek Key"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(aiSummaryState = AiSummaryState.Generating) }
            when (val outcome = aiRepository.generateSummary(currentArticleId)) {
                is AiRepository.SummaryOutcome.Success -> {
                    val refreshed = repository.getArticle(currentArticleId)
                    _uiState.update { it.copy(aiSummaryState = AiSummaryState.Idle, article = refreshed) }
                }
                is AiRepository.SummaryOutcome.Failure ->
                    _uiState.update { it.copy(aiSummaryState = AiSummaryState.Failed(outcome.userMessage)) }

                AiRepository.SummaryOutcome.OutOfBudget ->
                    _uiState.update {
                        it.copy(aiSummaryState = AiSummaryState.Failed("今日 AI 额度已用尽，明天再试"))
                    }
            }
        }
    }

    /** 翻译开/关：Shown → 切回原文并取消进行中的翻译（None，缓存保留）；None/Failed → 发起翻译。 */
    private fun toggleTranslation() {
        when (_uiState.value.translationState) {
            is TranslationState.Shown ->
                _uiState.update { it.copy(translationState = TranslationState.None) }

            is TranslationState.Progressing -> {
                translationJob?.cancel()
                translationJob = null
                _uiState.update { it.copy(translationState = TranslationState.None) }
            }

            is TranslationState.Failed, TranslationState.None -> startTranslation()
        }
    }

    /** 清缓存重译（重译按钮）。进行中不可重译。 */
    private fun retranslate() {
        if (_uiState.value.translationState is TranslationState.Progressing) return
        aiRepository.clearTranslationCache(currentArticleId)
        startTranslation()
    }

    private fun startTranslation() {
        if (!aiStore.hasKey()) {
            _uiState.update {
                it.copy(translationState = TranslationState.Failed("未配置 API Key，请到「我的」页设置 DeepSeek Key"))
            }
            return
        }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update { it.copy(translationState = TranslationState.Progressing(emptyList())) }
            when (val outcome = aiRepository.translate(currentArticleId) { progress ->
                // 渐进回调：每翻完一段推一版；缓存命中时首帧即全量
                val segments = progress.chunks.mapIndexed { i, chunk ->
                    TranslationSegmentUi(
                        originalHtml = chunk.html,
                        translatedHtml = progress.translated[i],
                        blocks = chunk.blocks,
                    )
                }
                _uiState.update { it.copy(translationState = TranslationState.Progressing(segments)) }
            }) {
                is AiRepository.TranslationOutcome.Success ->
                    _uiState.update { state ->
                        val current = state.translationState
                        if (current is TranslationState.Progressing) {
                            state.copy(translationState = TranslationState.Shown(current.segments))
                        } else {
                            state
                        }
                    }

                is AiRepository.TranslationOutcome.AlreadyChinese ->
                    _uiState.update {
                        it.copy(translationState = TranslationState.Failed("原文已是中文，无需翻译"))
                    }

                is AiRepository.TranslationOutcome.Failure ->
                    _uiState.update { it.copy(translationState = TranslationState.Failed(outcome.userMessage)) }

                AiRepository.TranslationOutcome.OutOfBudget ->
                    _uiState.update {
                        it.copy(translationState = TranslationState.Failed("今日 AI 额度已用尽，明天再试"))
                    }
            }
        }
    }

    private fun toggleStarred() {
        val current = _uiState.value.article?.article ?: return
        viewModelScope.launch {
            repository.setStarred(current.id, !current.isStarred)
            val updated = current.copy(isStarred = !current.isStarred)
            _uiState.update { it.copy(article = it.article?.copy(article = updated)) }
        }
    }

    private fun toggleBookmarked() {
        val current = _uiState.value.article?.article ?: return
        viewModelScope.launch {
            repository.setBookmarked(current.id, !current.isBookmarked)
            val updated = current.copy(isBookmarked = !current.isBookmarked)
            _uiState.update { it.copy(article = it.article?.copy(article = updated)) }
        }
    }

    /**
     * 划词后落标注。跨块选择会给出多个引文（选择容器按文本 composable 切分），
     * 每块各落一条——块内锚定是按块文本找回位置的，把两块粘成一条就再也找不回来。
     */
    fun addAnnotations(quotes: List<String>, colorIndex: Int, note: String? = null) {
        val articleId = _uiState.value.article?.article?.id ?: return
        val texts = quotes.map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return
        val trimmedNote = note?.trim()?.takeUnless { it.isEmpty() }
        viewModelScope.launch {
            texts.forEach { text ->
                annotationRepository.add(
                    articleId = articleId,
                    quote = text,
                    prefix = "",
                    suffix = "",
                    startOffset = 0,
                    endOffset = text.length,
                    color = colorIndex,
                    note = trimmedNote,
                )
            }
        }
    }

    fun addAnnotation(quote: String, color: Int, note: String? = null) =
        addAnnotations(listOf(quote), color, note)

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { annotationRepository.delete(id) }
    }
}

/** 抓取失败的结构化原因：VM 不产文案，翻译在 UI 层。 */
sealed interface FetchFailReason {
    data class FromFailure(val failure: FetchFailure) : FetchFailReason
    data object FeedDisabled : FetchFailReason
    data object ArticleMissing : FetchFailReason
    data class ShorterThanExisting(val got: Int, val existing: Int) : FetchFailReason
}
