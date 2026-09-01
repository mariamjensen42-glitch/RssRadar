package com.cycling.rssradar.ui.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.db.ArticleEntity
import com.cycling.rssradar.data.db.ArticleWithFeed
import com.cycling.rssradar.data.FeedRepository
import com.cycling.rssradar.data.OnDemandFetch
import com.cycling.rssradar.data.ai.AiRepository
import com.cycling.rssradar.data.store.AiStore
import com.cycling.rssradar.data.store.LinkShareState
import com.cycling.rssradar.data.store.LinkStore
import com.cycling.rssradar.data.store.ReadingPrefs
import com.cycling.rssradar.data.store.ReadingPrefsStore
import com.cycling.rssradar.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
}

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
     * 本段原文内的顶层块（双语对照的配对单位）。由 [com.cycling.rssradar.data.ai.TranslationSegments]
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
) : ViewModel(), MviViewModel<ArticleDetailIntent> {

    private val _article = MutableStateFlow<ArticleWithFeed?>(null)
    val article: StateFlow<ArticleWithFeed?> = _article.asStateFlow()

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
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _article.value = repository.getArticle(articleId)
            _neighbors.value = loadNeighbors(articleId)
            if (_article.value?.article?.isRead == false) {
                repository.markRead(articleId)
            }
            fetchFullContentIfNeeded(articleId)
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
