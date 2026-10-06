package com.cycling.rssradar.ui.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ArrowUpToLine
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.data.db.ArticleAnnotationEntity
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.AiFeatureSettings
import com.cycling.rssradar.core.model.LinkShareState
import com.cycling.rssradar.core.model.ReadingPrefs
import com.cycling.rssradar.core.model.ReadingRenderer
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.theme.LocalRadarColors
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.isLightBackground
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.data.platform.shareArticle
import com.cycling.rssradar.core.ui.theme.ApplySystemBarIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * 全屏查看页的入参（issue #60）：本文图片列表 + 起始下标。
 * 刷新列表在读屏时惰性算一次，只在用户真点图时才付 jsoup 解析的代价。
 */
private data class ImageViewer(val images: List<String>, val index: Int)

/**
 * 阅读页外壳：只负责把**阅读主题**（#16）装到 CompositionLocal 上。
 *
 * 为什么包在外面而不是逐处取色：阅读页有上百处 `radarColors()`（顶栏、底栏、
 * 卡片、正文、WebView 注入的 CSS 全读它），逐处改必然漏。这里整页覆盖
 * [LocalRadarColors]，下游零改动自动跟随；系统栏图标也跟着阅读页底色翻。
 */
/** 阅读页的全部命令出口：Screen 不持有 VM，只从 [ArticleDetailActions] 发起动作。 */
class ArticleDetailActions(
    val onIntent: (ArticleDetailIntent) -> Unit,
    val onLoad: (Long) -> Unit,
    val onReadingRatio: (Long) -> Float?,
    val onRememberReadingRatio: (Long, Float) -> Unit,
    val onUpdateReadingPrefs: ((ReadingPrefs) -> ReadingPrefs) -> Unit,
    val onAddAnnotations: (List<String>, Int, String?) -> Unit,
)

@Composable
fun ArticleDetailDestination(
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
    /** 相关阅读卡片点击跳转（AiFeature.RELATED）。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 标注列表页入口（顶栏溢出菜单）。 */
    onOpenAnnotations: () -> Unit = {},
    /** 打开音频播放页（有音频地址的文章才有入口）。 */
    onOpenAudio: (Long) -> Unit = {},
    viewModel: ArticleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = remember(viewModel) {
        ArticleDetailActions(
            onIntent = viewModel::onIntent,
            onLoad = viewModel::load,
            onReadingRatio = viewModel::readingRatio,
            onRememberReadingRatio = viewModel::rememberReadingRatio,
            onUpdateReadingPrefs = viewModel::updateReadingPrefs,
            onAddAnnotations = viewModel::addAnnotations,
        )
    }

    ArticleDetailScreen(
        state = state,
        actions = actions,
        articleId = articleId,
        onBack = onBack,
        onOpenOriginal = onOpenOriginal,
        onOpenArticle = onOpenArticle,
        onOpenAnnotations = onOpenAnnotations,
        onOpenAudio = onOpenAudio,
    )
}

@Composable
fun ArticleDetailScreen(
    state: ArticleDetailUiState,
    actions: ArticleDetailActions,
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
    /** 相关阅读卡片点击跳转（AiFeature.RELATED）。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 标注列表页入口（顶栏溢出菜单）。 */
    onOpenAnnotations: () -> Unit = {},
    /** 打开音频播放页（有音频地址的文章才有入口）。 */
    onOpenAudio: (Long) -> Unit = {},
) {
    val readingPrefs = state.readingPrefs
    val appColors = radarColors()
    val pageColors = remember(readingPrefs.readingTheme, appColors) {
        readingPrefs.readingTheme.pageColors(appColors)
    }
    // 深色模式下开「纸张」时状态栏图标必须变深色，否则一片糊。
    // 退出阅读页由 ApplySystemBarIcons 的 onDispose 还原成应用主题。
    ApplySystemBarIcons(darkTheme = !pageColors.isLightBackground())
    val annotations = state.annotations
    // 正文媒体播放器（ADR-0018）：页面级唯一实例，延迟创建。挂在这一层是为了让
    // 正文渲染（RenderNode 递归深处）、底栏视频入口、全屏播放页三者共用同一个播放器。
    val media = rememberArticleMediaPlayer()
    // 换篇即停：播放器是页面级的，不留上一篇的媒体在新文章里继续出声
    LaunchedEffect(articleId) { media.stop() }
    CompositionLocalProvider(
        LocalRadarColors provides pageColors,
        LocalReadingAnnotations provides annotations,
        LocalArticleMediaPlayer provides media,
    ) {
        ArticleDetailBody(
            state = state,
            actions = actions,
            articleId = articleId,
            onBack = onBack,
            onOpenOriginal = onOpenOriginal,
            onOpenArticle = onOpenArticle,
            onOpenAnnotations = onOpenAnnotations,
            onOpenAudio = onOpenAudio,
        )
    }
}

@Composable
private fun ArticleDetailBody(
    state: ArticleDetailUiState,
    actions: ArticleDetailActions,
    articleId: Long,
    onBack: () -> Unit,
    onOpenOriginal: (String) -> Unit = {},
    /** 相关阅读卡片点击跳转（AiFeature.RELATED）。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 标注列表页入口（顶栏溢出菜单）。 */
    onOpenAnnotations: () -> Unit = {},
    /** 打开音频播放页（有音频地址的文章才有入口）。 */
    onOpenAudio: (Long) -> Unit = {},
) {
    val article = state.article
    val media = LocalArticleMediaPlayer.current
    val initialLoadDone = state.initialLoadDone
    val isFetchingContent = state.isFetchingContent
    val contentFetchState = state.contentFetch
    val preferSummary = state.preferSummary
    val aiSummaryState = state.aiSummaryState
    val translationState = state.translationState
    val neighbors = state.neighbors
    val readingPrefs = state.readingPrefs
    val linkShare = state.linkShare
    val aiArtifacts = state.aiArtifacts
    val aiRunning = state.aiRunning
    val aiMessage = state.aiMessage
    val aiEnabledFeatures = state.aiEnabledFeatures
    val aiKeyConfigured = state.aiKeyConfigured
    // 分享文章（#26）需要 Context 起系统分享面板
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showStyleSheet by remember { mutableStateOf(false) }
    // AI 分析面板：瞬时 UI，与排版面板同为 ModalBottomSheet，不入路由
    var showAiSheet by remember { mutableStateOf(false) }
    // 全屏图片查看（issue #60）：瞬时 UI，不入路由、不占 back 栈
    var imageViewer by remember { mutableStateOf<ImageViewer?>(null) }
    // 页内查找：瞬时 UI，同不进路由。query 一变游标就回到第一处。
    var findActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findCursor by remember { mutableStateOf(0) }
    var findCount by remember { mutableStateOf(0) }
    // 划词：[SelectionState] 暴露选中文本。选区偏移是 internal，但块内锚定本来就不依赖偏移，
    // 只要有引文就能把它找回来。
    val selectionState = rememberSelectionState()
    val selectionTexts = selectionState.selectedTexts
    val selectionBlocks = remember(selectionTexts) {
        selectionTexts.map { it.text }.filter { it.isNotBlank() }
    }
    // 划词工具条的落位锚点：长按点相对浮层容器左上角的偏移。
    // 公开 SelectionState 只有文本、没有坐标（内部那个不给契约），所以在长按发生时
    // 自己记一笔坐标；没记到就退回底部居中（IntOffset.Zero 表示"未捕捉"）。
    var selectionAnchor by remember { mutableStateOf(PRESET_BOTTOM_ANCHOR) }
    // 整页滚动状态提升到 Screen：顶栏标题「滚出视口才出现」需要读滚动量
    val scrollState = rememberScrollState()
    // 到顶 / 到底（右下角悬浮按钮）：用递增序号而不是裸目标 —— 连点两次同一个目标时
    // 目标值不变，下游按值判重会把它当成重复请求吞掉。
    var jumpRequest by remember { mutableStateOf<JumpRequest?>(null) }
    // 视口模式（有图文章）的头部折叠量 = WebView 内部滚动量，同样驱动顶栏补位标题
    var headerScrollY by remember { mutableStateOf(0) }
    // 视口模式的可滚动上限（WebView 内部滚动范围）：与 headerScrollY 一起算阅读位置比例
    var headerMaxScroll by remember { mutableStateOf(0) }
    // 本次打开要恢复的位置（比例）。只取一次、之后不再变：切译文/换排版引起的重组不该再滚一遍
    var restoreRatio by remember { mutableStateOf<Float?>(null) }
    // 标题完全滚出视口所需的滚动量（标题 top + 高度，onGloballyPositioned 量出）。
    // 初值 Int.MAX_VALUE = 未量出前顶栏不显标题。
    var titleHideOffset by remember { mutableStateOf(Int.MAX_VALUE) }
    // 工具栏随滚动自动隐藏（ReadYou 差距表 #22）。两种滚动容器只有一个在动：
    // 整页模式走 scrollState，视口模式走 WebView 内部滚动量 headerScrollY，
    // 取二者较大值即当前真实滚动位置。
    // 判据在 AutoHideBars.kt（纯函数，JVM 可测）；这里只负责采样与写状态，
    // 且只在「显隐翻转」时更新——每帧都写会在滚动中引发无谓的重组。
    val autoHideBars = readingPrefs.autoHideBars
    var barsVisible by remember { mutableStateOf(true) }
    // 「到顶 / 到底」按钮的方向：往下读就给「到底」，往上翻就给「到顶」。
    // 单开一个监听、不复用下面那个 —— 那个在关掉 autoHideBars 时会直接 return，
    // 方向判定不能跟着一起失效（关掉自动隐藏只该让工具栏常显）。
    var jumpTarget by remember { mutableStateOf(JumpTarget.BOTTOM) }
    // 只在滚动时浮出、静置一会儿就收 —— 常驻会一直压在右下角的正文上。
    var jumpVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var lastJumpY = 0
        snapshotFlow { maxOf(scrollState.value, headerScrollY) }
            // collectLatest：下一个滚动值一到就取消这里的 delay，于是「停下来静置」才会计时到点
            // 收起。不用额外记一个"最后滚动时刻"（那会让每帧都写 state、每帧重组）。
            .collectLatest { y ->
                jumpVisible = true
                val delta = y - lastJumpY
                when {
                    delta > BARS_SCROLL_SLOP -> jumpTarget = JumpTarget.TOP
                    delta < -BARS_SCROLL_SLOP -> jumpTarget = JumpTarget.BOTTOM
                }
                lastJumpY = y
                delay(JUMP_AUTO_HIDE_MS)
                jumpVisible = false
            }
    }
    LaunchedEffect(autoHideBars) {
        if (!autoHideBars) {
            barsVisible = true
            return@LaunchedEffect
        }
        var lastY = 0
        snapshotFlow { maxOf(scrollState.value, headerScrollY) }
            .collect { y ->
                val next = nextBarsVisible(autoHideBars, lastY, y, barsVisible)
                lastY = y
                if (next != barsVisible) barsVisible = next
            }
    }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(articleId) {
        actions.onLoad(articleId)
        scrollState.scrollTo(0)
        headerScrollY = 0
        headerMaxScroll = 0
        titleHideOffset = Int.MAX_VALUE
        imageViewer = null
        findActive = false
        findQuery = ""
        findCursor = 0
        findCount = 0
        // 阅读位置记忆：换文章时取一次上次读到的位置，交给正文区按自己的滚动宿主恢复
        restoreRatio = actions.onReadingRatio(articleId)
    }
    // 翻译失败走 Snackbar（spec #44：正文保持原文，报错可重试）；按状态实例触发，不会重复弹
    LaunchedEffect(translationState) {
        if (translationState is TranslationState.Failed) {
            snackbarHostState.showSnackbar((translationState as TranslationState.Failed).message)
        }
    }
    // AI 提示刻意**不走** Snackbar：提示产生在 AI 面板里，Snackbar 会被面板挡住看不见，
    // 而且这里一旦消费掉 message，面板里就永远读不到它了。提示由 AiArticleSheet 自己渲染，
    // 并在下一次操作（点按钮/提问）或关闭面板时消费。

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        // 到顶 / 到底：走 Scaffold 的 FAB 槽位 —— 它会自动避让 bottomBar 与系统栏，
        // 且位于 content 之上。自己往内容区里摆会落到操作栏底下，点不到。
        floatingActionButton = {
            AnimatedVisibility(
                visible = jumpVisible,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn() + scaleIn(initialScale = 0.85f)
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    fadeOut() + scaleOut(targetScale = 0.85f)
                },
            ) {
                ReadingJumpButton(
                    target = jumpTarget,
                    onClick = {
                        jumpRequest = JumpRequest((jumpRequest?.seq ?: 0L) + 1L, jumpTarget)
                    },
                )
            }
        },
        topBar = {
            // 收起用 shrink/expand 而不是 slide：Scaffold 的 content padding 按这两个
            // 槽位的实测高度算，只有高度真的动画到 0，正文区才平滑地吃掉这块空间；
            // 用位移动画的话高度在动画结束瞬间突变，正文会"跳"一下。
            AnimatedVisibility(
                visible = barsVisible,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn() + expandVertically(expandFrom = Alignment.Top)
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                },
            ) {
            ArticleDetailTopBar(
                title = article?.article?.title,
                // 两种模式任一把标题滚出视口都补位显示
                showTitle = scrollState.value >= titleHideOffset ||
                    headerScrollY >= titleHideOffset,
                onBack = onBack,
                onOpenStyle = { showStyleSheet = true },
                onOpenFind = { findActive = true },
                onOpenAnnotations = onOpenAnnotations,
                onShare = {
                    article?.let { item ->
                        context.shareArticle(
                            title = item.article.title,
                            link = item.article.link,
                            summary = item.article.summary,
                            state = linkShare,
                        )
                    }
                },
                onToggleTranslation = { actions.onIntent(ArticleDetailIntent.ToggleTranslation) },
                isShowingTranslation = translationState is TranslationState.Shown ||
                    translationState is TranslationState.Progressing,
                isGeneratingTranslation = translationState is TranslationState.Progressing,
                aiSummary = article?.article?.aiSummary,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = { actions.onIntent(ArticleDetailIntent.GenerateSummary) },
            )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = barsVisible,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn() + expandVertically(expandFrom = Alignment.Bottom)
                },
                exit = if (reducedMotion) {
                    ExitTransition.None
                } else {
                    fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                },
            ) {
            article?.let { item ->
                ArticleActionsBar(
                    isStarred = item.article.isStarred,
                    isBookmarked = item.article.isBookmarked,
                    hasPrev = neighbors.prevId != null,
                    hasNext = neighbors.nextId != null,
                    onPrev = { neighbors.prevId?.let(actions.onLoad) },
                    onNext = { neighbors.nextId?.let(actions.onLoad) },
                    onStar = { actions.onIntent(ArticleDetailIntent.ToggleStarred) },
                    onBookmark = { actions.onIntent(ArticleDetailIntent.ToggleBookmarked) },
                    onOpenOriginal = { onOpenOriginal(item.article.link) },
                    onOpenAi = { showAiSheet = true },
                    // 只有真的拿到音频地址才给入口：mediaKind 说有音频但地址没落库时，
                    // 点了会进到"没有音频"的空页，不如不给
                    onPlayAudio = item.article.mediaUrl
                        ?.takeIf { it.isNotBlank() && item.article.mediaKind == ArticleEntity.MEDIA_KIND_AUDIO }
                        ?.let { { onOpenAudio(item.article.id) } },
                    // 视频同理（ADR-0018）：拿到直链才给入口，点开是全屏播放页。
                    // 正文里的视频另有内嵌播放，这条只覆盖订阅源级 enclosure 视频——
                    // 它不在正文 HTML 里，正文区没有它的位置。
                    onPlayVideo = item.article.mediaUrl
                        ?.takeIf { it.isNotBlank() && item.article.mediaKind == ArticleEntity.MEDIA_KIND_VIDEO }
                        ?.let { url -> media?.let { player -> { player.requestFullscreen(url) } } },
                )
            }
            }
        },
    ) { padding ->
        val current = article
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (initialLoadDone) {
                    // 查过了、确实没有，才可以说「文章不存在」
                    Text(stringResource(R.string.article_not_found), color = radarColors().textSecondary)
                } else {
                    // 首查进行中（issue #73）：此前的 null 会闪一帧「文章不存在」
                    CircularProgressIndicator(
                        color = radarColors().accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            return@Scaffold
        }
        // 查找的能力边界要如实标出：译文分段渲染没有接查找；整页 WebView（无图、由外层
        // Compose 滚动）里平台的 findNext 带不动视口。两者都不该让「下一处」看起来能用。
        val translationActive = translationState is TranslationState.Shown ||
            translationState is TranslationState.Progressing
        val viewportBody = shouldUseViewport(BodyMode.WEBVIEW, current.article.content)
        // 整页模式的跳转在这里执行；视口模式由 WebView 自己消费同一个 request
        //（那条路上滚动宿主是 WebView，外层 Compose 的 scrollState 根本不动）。
        LaunchedEffect(jumpRequest?.seq) {
            val request = jumpRequest ?: return@LaunchedEffect
            if (!viewportBody) {
                scrollState.animateScrollTo(
                    if (request.target == JumpTarget.TOP) 0 else scrollState.maxValue,
                )
            }
        }
        val findLimit = when {
            translationActive -> FindLimit.UNSUPPORTED
            readingPrefs.renderer != ReadingRenderer.NATIVE && !viewportBody -> FindLimit.NO_AUTO_SCROLL
            else -> FindLimit.NONE
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // 越界切篇（#23）：挂在滚动容器的祖先上，只观察不消费滚动量。
                    // 视口模式由 WebView 内部滚动，拿不到越界量，那里不生效。
                    .pullToSwitchArticle(
                        enabled = readingPrefs.pullToSwitchArticle,
                        atTop = { scrollState.value == 0 },
                        atBottom = { scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue },
                        hasPrev = neighbors.prevId != null,
                        hasNext = neighbors.nextId != null,
                        onSwitch = { target ->
                            when (target) {
                                PullTarget.PREVIOUS -> neighbors.prevId?.let(actions.onLoad)
                                PullTarget.NEXT -> neighbors.nextId?.let(actions.onLoad)
                                PullTarget.NONE -> Unit
                            }
                        },
                    ),
            ) {
                if (findActive) {
                    ReaderFindBar(
                        query = findQuery,
                        onQueryChange = { value ->
                            findQuery = value
                            findCursor = 0
                        },
                        count = findCount,
                        // 展示用 1 基：读者说「第 3 处」，不关心它是 0 基的下标
                        cursor = if (findCount == 0) 0 else findCursor + 1,
                        onPrevious = {
                            if (findCount > 0) findCursor = (findCursor - 1 + findCount) % findCount
                        },
                        onNext = {
                            if (findCount > 0) findCursor = (findCursor + 1) % findCount
                        },
                        onClose = {
                            findActive = false
                            findQuery = ""
                            findCursor = 0
                            findCount = 0
                        },
                        limit = findLimit,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // 划词：SelectionContainer 只提供选择能力，选中后的动作在工具条。
                // 它不含 lazy 布局的直接子级（相关阅读条在它外面），不触到未定义行为。
                SelectionContainer(
                    state = selectionState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 长按点捕捉：用于把工具条定位到手指附近。放在 SelectionContainer
                        // 的 modifier 上（容器本身消费长按做选区），用 detectTapGestures 只
                        // 观察不消费——不传 onLongPress 给它，避免抢掉划词选择。
                        .captureLongPressRootAnchor { selectionAnchor = it },
                ) {
                    ReadingBody(
                        article = current,
                        isFetchingContent = isFetchingContent,
                        contentFetchState = contentFetchState,
                        onRetryFetch = { actions.onIntent(ArticleDetailIntent.RetryFetch) },
                        preferSummary = preferSummary,
                        onShowFullContent = {
                            actions.onIntent(ArticleDetailIntent.SetPreferSummary(false))
                        },
                        aiSummaryState = aiSummaryState,
                        translationState = translationState,
                        scrollState = scrollState,
                        headerScrollY = headerScrollY,
                        headerMaxScroll = headerMaxScroll,
                        restoreRatio = restoreRatio,
                        jumpRequest = jumpRequest,
                        onHeaderScroll = { y, max ->
                            headerScrollY = y
                            headerMaxScroll = max
                        },
                        onPositionChange = { actions.onRememberReadingRatio(articleId, it) },
                        onTitleMeasured = { titleHideOffset = it },
                        onGenerateSummary = { actions.onIntent(ArticleDetailIntent.GenerateSummary) },
                        onRetranslate = { actions.onIntent(ArticleDetailIntent.RetranslateArticle) },
                        onShowOriginal = { actions.onIntent(ArticleDetailIntent.ToggleTranslation) },
                        onTranslationDisplayChange = { next ->
                            actions.onUpdateReadingPrefs { it.copy(translation = next) }
                        },
                        onImageClick = { url -> imageViewer = openImageViewer(current, url) },
                        findQuery = if (findActive) findQuery else "",
                        findCursor = findCursor,
                        onFindCount = { findCount = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 相关阅读（AiFeature.RELATED）：横滑卡片条，仅在有候选时出现——
                // 空态不占高度，阅读区恢复满屏。
                val related = state.related
                if (related.isNotEmpty()) {
                    RelatedArticlesStrip(items = related, onOpen = onOpenArticle)
                }
            }
            // 到顶 / 到底的按钮不在这里 —— 它挂在 Scaffold 的 floatingActionButton 槽位，
            // 那个位置天生避让 bottomBar 且在 content 之上；放在本 Box 里会落到操作栏下面
            //（Scaffold 的 bottomBar 绘制在 content 之上），点了没反应。
            if (selectionBlocks.isNotEmpty()) {
                ReaderSelectionBar(
                    anchor = selectionAnchor,
                    modifier = Modifier.fillMaxSize(),
                    onHighlight = { colorIndex, note ->
                        actions.onAddAnnotations(selectionBlocks, colorIndex, note)
                        selectionState.clear()
                    },
                    onCopy = {
                        copyToClipboard(context, selectionBlocks.joinToString("\n"))
                        selectionState.clear()
                    },
                    onDismiss = { selectionState.clear() },
                )
            }
        }
    }

    imageViewer?.let { viewer ->
        ReaderImagePage(
            images = viewer.images,
            initialIndex = viewer.index,
            onDismiss = { imageViewer = null },
        )
    }

    // 全屏播放（ADR-0018）：与全屏看图一样是阅读页之上的瞬时 UI，不进路由。
    // 退出语义由播放器定：正文里有这条媒体的画面 ⇒ 只退出全屏、退回正文继续播；
    // 没有（订阅源级视频）⇒ 停掉，免得留下"看不见的声音"。
    media?.fullscreenUrl?.let { url ->
        ReaderVideoPage(
            url = url,
            title = article?.article?.title,
            media = media,
            resumeInline = media.hasInlineRenderer(url),
            onDismiss = { media.exitFullscreen() },
        )
    }

    if (showAiSheet) {
        AiArticleSheet(
            artifacts = aiArtifacts,
            running = aiRunning,
            message = aiMessage,
            enabled = aiEnabledFeatures.enabled,
            keyConfigured = aiKeyConfigured,
            onRun = { feature -> actions.onIntent(ArticleDetailIntent.RunAi(feature)) },
            onAsk = { question -> actions.onIntent(ArticleDetailIntent.AskArticle(question)) },
            onExplain = { term -> actions.onIntent(ArticleDetailIntent.ExplainTerm(term)) },
            onConsumeMessage = { actions.onIntent(ArticleDetailIntent.ConsumeAiMessage) },
            onDismiss = {
                showAiSheet = false
                // 关掉面板就清掉提示，免得下次打开先看到一条过期的失败原因
                actions.onIntent(ArticleDetailIntent.ConsumeAiMessage)
            },
        )
    }

    if (showStyleSheet) {
        ReadingStyleSheet(
            prefs = readingPrefs,
            onRenderer = { r -> actions.onUpdateReadingPrefs { it.copy(renderer = r) } },
            onFontSize = { v -> actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(fontSize = v)) } },
            onLineHeight = { v -> actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(lineHeight = v)) } },
            onPadding = { v ->
                actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(horizontalPadding = v)) }
            },
            onFontFamily = { v ->
                actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(fontFamily = v)) }
            },
            onLetterSpacing = { v ->
                actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(letterSpacing = v)) }
            },
            onTextAlign = { v ->
                actions.onUpdateReadingPrefs { it.copy(style = it.style.copy(textAlign = v)) }
            },
            onImageCornerRadius = { v ->
                actions.onUpdateReadingPrefs { it.copy(image = it.image.copy(cornerRadius = v)) }
            },
            onImageMaximize = { v ->
                actions.onUpdateReadingPrefs { it.copy(image = it.image.copy(maximizeOnTap = v)) }
            },
            onImmersive = { v ->
                actions.onUpdateReadingPrefs { it.copy(immersive = v) }
            },
            autoHideBars = readingPrefs.autoHideBars,
            onAutoHideBars = { v ->
                actions.onUpdateReadingPrefs { it.copy(autoHideBars = v) }
            },
            onReadingTheme = { v ->
                actions.onUpdateReadingPrefs { it.copy(readingTheme = v) }
            },
            onPullToSwitch = { v ->
                actions.onUpdateReadingPrefs { it.copy(pullToSwitchArticle = v) }
            },
            // 正文/摘要：只在两者实质不同时给（canSwitchToSummary），否则点了等于没点
            canSwitchToSummary = canSwitchToSummary(
                content = article?.article?.content,
                summary = article?.article?.summary,
            ),
            preferSummary = preferSummary,
            onPreferSummary = { v ->
                actions.onIntent(ArticleDetailIntent.SetPreferSummary(v))
            },
            onDismiss = { showStyleSheet = false },
        )
    }
}

/** 划词复制：写系统剪贴板，Android 13+ 会自带一条"已复制"的系统提示。 */
private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("annotation", text))
}

/**
 * 点图 → 全屏查看：现提取本文图片列表（jsoup 解析，只在点击时付代价）并定位下标。
 * 提取不到（例如地址来自 srcset、被 sanitize 改过）时退化成"只看这一张"。
 */
private fun openImageViewer(article: ArticleWithFeed, url: String): ImageViewer {
    val images = ReadingImages.extract(article.article.content.orEmpty())
    return if (url in images) {
        ImageViewer(images, images.indexOf(url))
    } else {
        ImageViewer(listOf(url), 0)
    }
}


@Preview(showBackground = true, name = "阅读页 · 加载中")
@Composable
private fun ArticleDetailScreenLoadingPreview() {
    RssRadarTheme(darkTheme = false) {
        ArticleDetailScreen(
            state = ArticleDetailUiState(),
            actions = ArticleDetailActions(
                onIntent = {},
                onLoad = {},
                onReadingRatio = { null },
                onRememberReadingRatio = { _, _ -> },
                onUpdateReadingPrefs = {},
                onAddAnnotations = { _, _, _ -> },
            ),
            articleId = 1L,
            onBack = {},
        )
    }
}

/**
 * 停止滚动后多久收起跳转按钮。
 *
 * 取一个「够看见、又立刻让开」的值：滚动中手指常有几百毫秒的自然停顿，
 * 定长了按钮会一直挂在正文上（用户反馈「不要那么长」）。600ms 够完成一次点击，
 * 停顿一结束就不挡内容。
 */
private const val JUMP_AUTO_HIDE_MS = 600L

/**
 * 阅读页右下角的跳转悬浮按钮：**只有一个**，图标与目标随滚动方向切换 ——
 * 往下读时指向「到顶」，往上翻时指向「到底」。
 *
 * 挂在 Scaffold 的 `floatingActionButton` 槽位上，所以这里不写位置与 inset：
 * 槽位自己会避让底部操作栏和系统导航栏。**常驻，不跟随自动隐藏的工具栏一起收** ——
 * 这个按钮恰恰在「刚滚完一段、工具栏已经收起」时最需要按到，跟栏一起消失等于没有。
 */
@Composable
private fun ReadingJumpButton(
    target: JumpTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toTop = target == JumpTarget.TOP
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = radarColors().surface1,
        contentColor = radarColors().textSecondary,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (toTop) Lucide.ArrowUpToLine else Lucide.ArrowDownToLine,
            contentDescription = stringResource(
                if (toTop) R.string.reader_jump_top else R.string.reader_jump_bottom,
            ),
        )
    }
}

@Preview(showBackground = true, name = "阅读页 · 深色加载中")
@Composable
private fun ArticleDetailScreenDarkLoadingPreview() {
    RssRadarTheme(darkTheme = true) {
        ArticleDetailScreen(
            state = ArticleDetailUiState(),
            actions = ArticleDetailActions(
                onIntent = {},
                onLoad = {},
                onReadingRatio = { null },
                onRememberReadingRatio = { _, _ -> },
                onUpdateReadingPrefs = {},
                onAddAnnotations = { _, _, _ -> },
            ),
            articleId = 1L,
            onBack = {},
        )
    }
}
