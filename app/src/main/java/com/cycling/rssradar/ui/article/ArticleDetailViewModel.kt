package com.cycling.rssradar.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.db.ArticleEntity
import com.cycling.rssradar.core.data.db.ArticleWithFeed
import com.cycling.rssradar.core.data.FeedRepository
import com.cycling.rssradar.core.data.OnDemandFetch
import com.cycling.rssradar.core.data.ai.AiArtifactRepository
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiFeatureRunner
import com.cycling.rssradar.core.data.ai.AiFulltextPayload
import com.cycling.rssradar.core.data.ai.AiParsers
import com.cycling.rssradar.core.data.ai.AiRepository
import com.cycling.rssradar.core.data.store.AiFeatureSettings
import com.cycling.rssradar.core.data.store.AiFeatureStore
import com.cycling.rssradar.core.data.store.AiStore
import com.cycling.rssradar.core.data.store.LinkShareState
import com.cycling.rssradar.core.data.store.LinkStore
import com.cycling.rssradar.core.data.store.ReadingPrefs
import com.cycling.rssradar.core.data.store.ReadingPrefsStore
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


/** 文章详情事件（候选 A，ADR-0003）。load 为生命周期，留 init，不进 Intent。 */
sealed interface ArticleDetailIntent {
    data object ToggleStarred : ArticleDetailIntent
    data object ToggleBookmarked : ArticleDetailIntent
    /** 生成/重新生成 AI 摘要（结果持久化在 articles.aiSummary）。 */
    data object GenerateSummary : ArticleDetailIntent
    /** 翻译开/关：未显示译文时发起翻译，显示中则切回原文。 */
    data object ToggleTranslation : ArticleDetailIntent
    /** 清缓存重译（issue #44：会话缓存 + 明确的重译按钮）。 */
    data object RetranslateArticle : ArticleDetailIntent

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
 * 展示顺序即按钮顺序：便宜且常用的在前，贵而重的在后。
 */
val ARTICLE_AI_BUTTONS: List<AiFeature> = listOf(
    AiFeature.OUTLINE,
    AiFeature.OPINION,
    AiFeature.CREDIBILITY,
    AiFeature.SHARE_COPY,
    AiFeature.FULLTEXT,
    AiFeature.TAGS,
    AiFeature.KEYWORDS,
    AiFeature.CLASSIFY,
    AiFeature.SENTIMENT,
    AiFeature.QUALITY,
    AiFeature.NOISE,
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
    /** 按需抓取原网页正文（ADR-0001）。直连模块，不经过 FeedRepository 转发。 */
    private val onDemandFetch: OnDemandFetch,
    private val readingPrefsStore: ReadingPrefsStore,
    private val linkStore: LinkStore,
    private val aiRepository: AiRepository,
    private val aiStore: AiStore,
    /** AI 智能功能模块：35 项功能的执行器与产物仓储。 */
    private val featureRunner: AiFeatureRunner,
    private val artifacts: AiArtifactRepository,
    private val featureStore: AiFeatureStore,
) : ViewModel(), MviViewModel<ArticleDetailIntent> {

    private val _article = MutableStateFlow<ArticleWithFeed?>(null)
    val article: StateFlow<ArticleWithFeed?> = _article.asStateFlow()

    /**
     * 首次详情查询是否已完成。初始 null ≠ 不存在——Room 查询是挂起调用，
     * 返回前 UI 若把 null 当「文章不存在」渲染，就会闪一帧错误提示再被真值覆盖。
     * 置 true 后不再回落：上一篇/下一篇切换时旧文章保持显示到新文章就位，同理不闪。
     */
    private val _initialLoadDone = MutableStateFlow(false)
    val initialLoadDone: StateFlow<Boolean> = _initialLoadDone.asStateFlow()

    /** 正在按需抓取原网页正文。失败是常态（反爬/JS 页），静默降级，UI 不弹错误。 */
    private val _isFetchingContent = MutableStateFlow(false)
    val isFetchingContent: StateFlow<Boolean> = _isFetchingContent.asStateFlow()

    private val _aiSummaryState = MutableStateFlow<AiSummaryState>(AiSummaryState.Idle)
    val aiSummaryState: StateFlow<AiSummaryState> = _aiSummaryState.asStateFlow()

    private val _translationState = MutableStateFlow<TranslationState>(TranslationState.None)
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()

    /** 同源上一篇/下一篇（底栏切换用），load 时随文章一起刷新。 */
    private val _neighbors = MutableStateFlow(ArticleNeighbors(prevId = null, nextId = null))
    val neighbors: StateFlow<ArticleNeighbors> = _neighbors.asStateFlow()

    /**
     * 本文已有的 AI 产物（feature.dbValue → 解析后的载荷）。
     * 只有解析出实质内容的才进表——空壳产物（如 `{"tags": []}`）按"尚未生成"处理，
     * 否则用户会看到一块什么都写不出来的空卡片。
     */
    private val _aiArtifacts = MutableStateFlow<Map<Int, Any>>(emptyMap())
    val aiArtifacts: StateFlow<Map<Int, Any>> = _aiArtifacts.asStateFlow()

    /** 正在生成的 feature.dbValue 集合，按钮置灰与转圈用。 */
    private val _aiRunning = MutableStateFlow<Set<Int>>(emptySet())
    val aiRunning: StateFlow<Set<Int>> = _aiRunning.asStateFlow()

    /** AI 面板的一次性提示（失败原因 / 额度用尽），由 UI 消费后清除。 */
    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    /**
     * 已开启的 AI 功能集合。面板要靠它把「未开启」的功能渲染成灰色并给出开启引导——
     * 35 项里绝大多数默认关闭，若按钮一律长成能点的样子，
     * 用户点下去只会得到一个静默失败，这是最糟糕的一类反馈。
     */
    val aiEnabledFeatures: StateFlow<AiFeatureSettings> = featureStore.state

    /**
     * 是否已配置 API Key。
     * 未配置时面板顶部直接给指引，而不是等用户点了某个功能、跑完一轮才被告知——
     * 少一次必然失败的等待。
     */
    private val _aiKeyConfigured = MutableStateFlow(aiStore.hasKey())
    val aiKeyConfigured: StateFlow<Boolean> = _aiKeyConfigured.asStateFlow()

    /** 当前文章 id，AI 操作的目标；load 时更新。 */
    private var currentArticleId: Long = -1L

    /** 连点上/下一篇时取消前一次未完成的加载，避免旧文章晚到覆盖新文章。 */
    private var loadJob: Job? = null

    /**
     * 阅读偏好（排版 / 图片 / 渲染器 / 译文显示）。偏好属 UI 环境而非业务事件，
     * 按 ADR-0003「纯函数与状态 producer 保持 fun」的先例走普通方法，不进 Intent 面；
     * 数据源与主题宿主注入的 [com.cycling.rssradar.ui.theme.LocalReadingPrefs] 是同一份 Store。
     *
     * 四项偏好合成一份 state，因此这里只暴露两个成员，而不是原先的八个
     * （四个 StateFlow + 四个写方法）。
     */
    val readingPrefs: StateFlow<ReadingPrefs> = readingPrefsStore.state

    fun updateReadingPrefs(transform: (ReadingPrefs) -> ReadingPrefs) {
        readingPrefsStore.update(transform)
    }

    /** 外链打开方式与分享格式（#26）：阅读页分享/打开链接时按此偏好执行。 */
    val linkShare: StateFlow<LinkShareState> = linkStore.state

    init {
        // articleId 来自 nav args（类型安全路由写入 SavedStateHandle，issue #32）。
        // 进程重建时 NavController 恢复 back stack 并重放 args，本 init 重新触发 → 当前文章不丢。
        // 兼容 Long / String 两种存法（Navigation 版本对 bundle 内类型处理有差异）。
        val articleId = savedStateHandle.get<Long>(KEY_ARTICLE_ID)
            ?: savedStateHandle.get<String>(KEY_ARTICLE_ID)?.toLongOrNull()
        if (articleId != null) load(articleId)
    }

    /** 幂等：init 自加载后，screen 生命周期处重复调用只是重查 DB。切换文章时重置 AI 过程状态。 */
    fun load(articleId: Long) {
        val isNewArticle = articleId != currentArticleId
        currentArticleId = articleId
        if (isNewArticle) {
            // 译文/摘要缓存都按文章 id 键控，换文章必须清过程状态并取消进行中的翻译，
            // 否则旧译文/旧进度会顶在新文章上
            translationJob?.cancel()
            translationJob = null
            _translationState.value = TranslationState.None
            _aiSummaryState.value = AiSummaryState.Idle
            _aiArtifacts.value = emptyMap()
            _aiRunning.value = emptySet()
            _aiMessage.value = null
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _article.value = repository.getArticle(articleId)
            _initialLoadDone.value = true
            _neighbors.value = loadNeighbors(articleId)
            if (_article.value?.article?.isRead == false) {
                repository.markRead(articleId)
            }
            // 推荐画像的采集点（ADR-0013）：每次打开都记，源亲和度的时间衰减靠它。
            // 这一列不参与内容状态刷新，也不影响已读语义。
            repository.markOpened(articleId)
            fetchFullContentIfNeeded(articleId)
            loadAiArtifacts(articleId)
        }
        // 每次进文章都重读一次：用户可能刚在设置页填了 Key 就切回来。
        _aiKeyConfigured.value = aiStore.hasKey()
    }

    /** 读本文已有的 AI 产物。与正文加载串行，避免同篇文章两个协程抢写 _article。 */
    private suspend fun loadAiArtifacts(articleId: Long) {
        val loaded = HashMap<Int, Any>()
        ARTICLE_AI_FEATURES.forEach { feature ->
            val raw = artifacts.rawOf(feature, articleId) ?: return@forEach
            val parsed = runCatching { AiParsers.parse(feature, raw) }.getOrNull() ?: return@forEach
            if (AiParsers.isMeaningful(feature, parsed)) loaded[feature.dbValue] = parsed
        }
        _aiArtifacts.value = loaded
    }

    /**
     * 把 AI 提取的正文写回文章并重载。
     *
     * 纯文本用标签剔除的方式得到：阅读时长、检索、以及后续所有 AI 分析的输入都吃
     * `contentText`，只写 HTML 会让它们全部落空。
     */
    private suspend fun applyExtractedContent(articleId: Long, html: String) {
        val plainText = stripHtmlTags(html)
        repository.applyExtractedContent(articleId, html, plainText)
        _article.value = repository.getArticle(articleId)
    }

    /** 去标签取纯文本。够用即可——这里只为了生成 contentText，不是要做 HTML 解析器。 */
    private fun stripHtmlTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun runAi(feature: AiFeature, question: String? = null) {
        val articleId = currentArticleId
        if (articleId < 0) return
        val key = feature.dbValue
        if (key in _aiRunning.value) return
        if (!featureStore.isEnabled(feature)) {
            _aiMessage.value = "「${feature.label}」未开启，可在设置里打开"
            return
        }
        viewModelScope.launch {
            _aiRunning.value = _aiRunning.value + key
            try {
                when (val outcome = featureRunner.run(feature, articleId, question)) {
                    is AiFeatureRunner.Outcome.Success -> {
                        val parsed = runCatching { AiParsers.parse(feature, outcome.payload) }.getOrNull()
                        if (parsed != null) {
                            _aiArtifacts.value = _aiArtifacts.value + (key to parsed)
                        }
                        // 全文提取的产物必须落回正文——只存进产物表等于用户什么都没看到。
                        if (feature == AiFeature.FULLTEXT && parsed is AiFulltextPayload && parsed.ok) {
                            applyExtractedContent(articleId, parsed.html)
                        }
                    }

                    is AiFeatureRunner.Outcome.Failed -> _aiMessage.value = outcome.message
                    AiFeatureRunner.Outcome.OutOfBudget -> _aiMessage.value = "今日 AI 额度已用尽，明天再试"
                    is AiFeatureRunner.Outcome.Skipped -> _aiMessage.value = outcome.reason
                }
            } catch (e: CancellationException) {
                // 协程取消（切文章、退出页面）必须原样抛出，吞掉会破坏结构化并发。
                throw e
            } catch (e: Exception) {
                // 兜底：任何漏网的异常都要把 loading 收掉并给出原因。
                // 少这层 try，一次异常就会让按钮永久转圈，而用户无从得知发生了什么。
                _aiMessage.value = "「${feature.label}」出错了：${e.message ?: e.javaClass.simpleName}"
            } finally {
                _aiRunning.value = _aiRunning.value - key
            }
        }
    }

    /** 同源相邻文章：按信息流列表序（新→旧）取当前文章前后各一篇。 */
    private suspend fun loadNeighbors(articleId: Long): ArticleNeighbors {
        val current = _article.value ?: return ArticleNeighbors(null, null)
        val ids = repository.getFeedArticleIds(current.article.feedId)
        val index = ids.indexOf(articleId)
        if (index == -1) return ArticleNeighbors(null, null)
        return ArticleNeighbors(
            prevId = ids.getOrNull(index + 1), // 列表序更靠后 = 发布更早
            nextId = ids.getOrNull(index - 1), // 列表序更靠前 = 发布更新
        )
    }

    /**
     * 文章没有 feed 自带正文时按需抓原网页（ADR-0001）。
     * 成功后重新加载文章；失败静默，详情页继续显示摘要。
     */
    private suspend fun fetchFullContentIfNeeded(articleId: Long) {
        val current = _article.value ?: return
        if (current.article.contentSource != ArticleEntity.CONTENT_SOURCE_NONE) return
        _isFetchingContent.value = true
        runCatching { onDemandFetch.fetch(articleId) }
        _isFetchingContent.value = false
        _article.value = repository.getArticle(articleId)
    }

    override fun onIntent(intent: ArticleDetailIntent) {
        when (intent) {
            ArticleDetailIntent.ToggleStarred -> toggleStarred()
            ArticleDetailIntent.ToggleBookmarked -> toggleBookmarked()
            ArticleDetailIntent.GenerateSummary -> generateSummary()
            ArticleDetailIntent.ToggleTranslation -> toggleTranslation()
            ArticleDetailIntent.RetranslateArticle -> retranslate()
            is ArticleDetailIntent.RunAi -> runAi(intent.feature)
            is ArticleDetailIntent.AskArticle ->
                if (intent.question.isNotBlank()) runAi(AiFeature.QA, intent.question.trim())

            is ArticleDetailIntent.ExplainTerm ->
                if (intent.term.isNotBlank()) runAi(AiFeature.GLOSSARY, intent.term.trim())

            ArticleDetailIntent.ConsumeAiMessage -> _aiMessage.value = null
        }
    }

    /** 生成 AI 摘要。未配置 Key 直接失败并引导；结果入库后重查文章刷新卡片。 */
    private fun generateSummary() {
        if (_aiSummaryState.value is AiSummaryState.Generating) return
        if (!aiStore.hasKey()) {
            _aiSummaryState.value = AiSummaryState.Failed("未配置 API Key，请到「我的」页设置 DeepSeek Key")
            return
        }
        viewModelScope.launch {
            _aiSummaryState.value = AiSummaryState.Generating
            when (val outcome = aiRepository.generateSummary(currentArticleId)) {
                is AiRepository.SummaryOutcome.Success -> {
                    _aiSummaryState.value = AiSummaryState.Idle
                    _article.value = repository.getArticle(currentArticleId)
                }
                is AiRepository.SummaryOutcome.Failure ->
                    _aiSummaryState.value = AiSummaryState.Failed(outcome.userMessage)
            }
        }
    }

    /** 翻译开/关：Shown → 切回原文并取消进行中的翻译（None，缓存保留）；None/Failed → 发起翻译。 */
    private fun toggleTranslation() {
        when (val state = _translationState.value) {
            is TranslationState.Shown -> _translationState.value = TranslationState.None
            is TranslationState.Progressing -> {
                translationJob?.cancel()
                translationJob = null
                _translationState.value = TranslationState.None
            }
            is TranslationState.Failed, TranslationState.None -> startTranslation()
        }
    }

    /** 清缓存重译（重译按钮）。进行中不可重译。 */
    private fun retranslate() {
        if (_translationState.value is TranslationState.Progressing) return
        aiRepository.clearTranslationCache(currentArticleId)
        startTranslation()
    }

    private var translationJob: Job? = null

    private fun startTranslation() {
        if (!aiStore.hasKey()) {
            _translationState.value = TranslationState.Failed("未配置 API Key，请到「我的」页设置 DeepSeek Key")
            return
        }
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _translationState.value = TranslationState.Progressing(emptyList())
            when (val outcome = aiRepository.translate(currentArticleId) { progress ->
                // 渐进回调：每翻完一段推一版；缓存命中时首帧即全量
                _translationState.value = TranslationState.Progressing(
                    progress.chunks.mapIndexed { i, chunk ->
                        TranslationSegmentUi(
                            originalHtml = chunk.html,
                            translatedHtml = progress.translated[i],
                            blocks = chunk.blocks,
                        )
                    },
                )
            }) {
                is AiRepository.TranslationOutcome.Success ->
                    (_translationState.value as? TranslationState.Progressing)?.let {
                        _translationState.value = TranslationState.Shown(it.segments)
                    }
                is AiRepository.TranslationOutcome.AlreadyChinese ->
                    _translationState.value = TranslationState.Failed("原文已是中文，无需翻译")
                is AiRepository.TranslationOutcome.Failure ->
                    _translationState.value = TranslationState.Failed(outcome.userMessage)
            }
        }
    }

    private fun toggleStarred() {
        val current = _article.value?.article ?: return
        viewModelScope.launch {
            repository.setStarred(current.id, !current.isStarred)
            _article.value = _article.value?.copy(
                article = current.copy(isStarred = !current.isStarred),
            )
        }
    }

    private fun toggleBookmarked() {
        val current = _article.value?.article ?: return
        viewModelScope.launch {
            repository.setBookmarked(current.id, !current.isBookmarked)
            _article.value = _article.value?.copy(
                article = current.copy(isBookmarked = !current.isBookmarked),
            )
        }
    }
}
