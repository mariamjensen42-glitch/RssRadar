package com.cycling.rssradar.ui.article

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.TranslationDisplayState
import com.cycling.rssradar.core.domain.reading.FindIndex
import com.cycling.rssradar.core.domain.reading.ReadingTextMap
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 页内查找在正文区的全部输入：一个打包参数，而不是四散五个。
 * 原生路要 [highlight]（叠底色）与 [focus]（滚过去）；WebView 路只要 [query]/[cursor]。
 */
internal data class ReadingFind(
    val query: String = "",
    val cursor: Int = 0,
    val highlight: FindHighlight? = null,
    val focus: AnchorFocus? = null,
)

@Composable

internal fun ReadingBody(
    article: ArticleWithFeed,
    isFetchingContent: Boolean,
    /**
     * 按需抓取的可见状态：失败要给出原因，不完整要说明是哪一类（ReadYou 的 Error 态同款）。
     * 此前这些原因只有诊断页看得到，读者只看到「正在获取全文…」然后什么都没有。
     */
    contentFetchState: ContentFetchState,
    /** 抓取失败后的重试出口（[ContentFetchState.Failed] 才出现）。 */
    onRetryFetch: () -> Unit,
    /** 读者手动切回订阅源摘要（[canSwitchToSummary] 为真时才有意义）。 */
    preferSummary: Boolean = false,
    /** 摘要态下「看正文」的回退出口：切换是单篇瞬时决定，必须有反向出口。 */
    onShowFullContent: () -> Unit = {},
    aiSummaryState: AiSummaryState,
    translationState: TranslationState,
    scrollState: ScrollState,
    /** 视口模式的头部折叠量（= WebView 内部滚动量），随滚驱动。 */
    headerScrollY: Int,
    onHeaderScroll: (Int) -> Unit,
    onTitleMeasured: (Int) -> Unit,
    onGenerateSummary: () -> Unit,
    onRetranslate: () -> Unit,
    onShowOriginal: () -> Unit,
    /** 译文显示偏好（纯译文/双语、上下/左右）变化出口，VM 写回持久化 Store。 */
    onTranslationDisplayChange: (TranslationDisplayState) -> Unit,
    /**
     * 正文图片点击（ReadYou 差距表第 19 项）：收到的是图片地址，Screen 用它打开全屏查看页
     * 并定位到对应那张。WebView 路走"img 包 a + 拦截 URL"，原生路走 Compose clickable，
     * 两条路都收敛到这一个出口。
     */
    onImageClick: (String) -> Unit,
    /** 页内查找的查询词；空串 = 未在查找（查找栏已关闭）。 */
    findQuery: String = "",
    /** 当前命中序号（0 基），用于把「下一处」滚进视口。 */
    findCursor: Int = 0,
    /** 命中总数上报：查找栏显示「n/N」，也用来判断「没有命中」。 */
    onFindCount: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 水平边距不放在外层：正文 WebView 的边距由排版设置的 CSS padding 控制（issue #42），
    // 头部（源名/标题）保持固定 20dp 不随排版项变化。
    // 翻译激活（渐进中或已完成）时正文走 TranslationReader（原生分段渲染），
    // 强制整页分支——视口折叠依赖 WebView 内部滚动，原生路驱动不了（与正文原生路同因）。
    val translationUi = when (val state = translationState) {
        is TranslationState.Progressing -> state
        is TranslationState.Shown -> state
        else -> null
    }
    val translationSegments: List<TranslationSegmentUi> = when (val state = translationState) {
        is TranslationState.Progressing -> state.segments
        is TranslationState.Shown -> state.segments
        else -> emptyList()
    }
    val renderer = LocalReadingPrefs.current.renderer
    val immersive = LocalReadingPrefs.current.immersive
    // 渲染模式与它所需的产物一次算清：判据本身要解析 HTML 才能知道"解析一无所获"，
    // 分开算就是同一份 HTML 解析两遍。纯函数，见 BodyMode.kt（可 JVM 单测）。
    // 导航丝滑（用户反馈）：长文 HTML 解析 + 图片正则提取是几十毫秒级的主线程阻塞，
    // 以前在 remember 里同步跑，正好砸在导航动画的帧上——表现为动画期间空白卡顿、
    // 正文"加载完才蹦出来"。改为后台线程计算，头部（源名/标题）立即渲染，解析完
    // 正文无缝接上；null = 还在算，正文区暂时留白。
    var plan by remember(
        translationUi,
        translationSegments,
        article.article.content,
        article.article.summary,
        renderer,
        immersive,
        preferSummary,
    ) {
        mutableStateOf<BodyPlan?>(null)
    }
    LaunchedEffect(
        translationUi,
        translationSegments,
        article.article.content,
        article.article.summary,
        renderer,
        immersive,
        preferSummary,
    ) {
        plan = withContext(Dispatchers.Default) {
            resolveBodyPlan(
                translationActive = translationUi != null,
                translationSegments = translationSegments,
                content = article.article.content,
                summary = article.article.summary,
                renderer = renderer,
                preferSummary = preferSummary,
                immersive = immersive,
            )
        }
    }
    // 后台解析尚未出结果：头部先上屏（导航动画期间用户看到的就是它），正文区留白
    val resolvedPlan = plan
    if (resolvedPlan == null) {
        Column(modifier = modifier.padding(vertical = 8.dp)) {
            ArticleHeader(
                article = article,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = onGenerateSummary,
                translationUi = translationUi,
                onRetranslate = onRetranslate,
                onShowOriginal = onShowOriginal,
                onTranslationDisplayChange = onTranslationDisplayChange,
                onTitleMeasured = onTitleMeasured,
            )
        }
        return
    }
    // OOM 防线（闪退诊断）：整页包高的 WebView 会被 Chromium 视为全部内容可见，
    // 有图文章的所有图片同时解码进 Java 堆，图多必 OOM（256MB 堆几十秒吃满）。
    // 只有含图的 WebView 路受限，原生路与译文路没有这个约束。
    // 摘要模式下「当前正文」是 summary 而不是 content：视口判定与图片列表都得换源，
    // 否则会出现「按 content 判定无图 → 整页 WebView，实际渲染的是带图的摘要」这种错位。
    val bodyHtml = if (resolvedPlan.summaryMode) article.article.summary else article.article.content
    val viewport = shouldUseViewport(resolvedPlan.mode, bodyHtml)
    // 全屏查看页的多图列表与点击分流共用这一份；只有 WebView 路需要（译文路与原生路
    // 由 Compose 直接处理图片点击）。空串/无图正文 → 空集合，自动静默。
    // 与 plan 同批后台算：同为主线程正则，同样会卡导航动画的帧。
    var imageUrls by remember(resolvedPlan.mode, bodyHtml) {
        mutableStateOf(emptyList<String>())
    }
    LaunchedEffect(resolvedPlan.mode, bodyHtml) {
        if (resolvedPlan.mode == BodyMode.WEBVIEW) {
            imageUrls = withContext(Dispatchers.Default) {
                bodyHtml?.let { ReadingImages.extract(it) } ?: emptyList()
            }
        } else {
            imageUrls = emptyList()
        }
    }

    // 页内查找：把中间树展平成带坐标的全文（[ReadingTextMap]）在全文上定位命中，
    // 再经 [TextBlock.anchor] 映射回「哪个顶层节点」用于滚动。
    // 只在原生路有效；WebView 路交给它自己的 findNext 滚，命中数从那边上报。
    val textBlocks = remember(resolvedPlan.nativeNodes) {
        ReadingNodes.textBlocks(resolvedPlan.nativeNodes)
    }
    val findHits = remember(textBlocks, findQuery) {
        if (resolvedPlan.mode != BodyMode.NATIVE || findQuery.isBlank()) {
            emptyList()
        } else {
            FindIndex.find(ReadingTextMap.flatten(textBlocks.map { it.text }), findQuery)
        }
    }
    LaunchedEffect(findHits.size, resolvedPlan.mode) {
        if (resolvedPlan.mode == BodyMode.NATIVE) onFindCount(findHits.size)
    }
    val activeHit = findHits.getOrNull(findCursor)
    val findHighlight = if (resolvedPlan.mode == BodyMode.NATIVE && findQuery.isNotBlank()) {
        FindHighlight(
            query = findQuery,
            activeBlockText = activeHit?.let { textBlocks.getOrNull(it.blockIndex)?.text },
            activeLocalStart = activeHit?.localStart ?: -1,
        )
    } else {
        null
    }
    val anchorFocus = activeHit?.let { hit ->
        textBlocks.getOrNull(hit.blockIndex)?.let { AnchorFocus(it.anchor, findCursor) }
    }
    val find = ReadingFind(
        query = findQuery,
        cursor = findCursor,
        highlight = findHighlight,
        focus = anchorFocus,
    )

    if (viewport) {
        Column(modifier = modifier.padding(vertical = 8.dp)) {
            // 视口模式的"随滚"体验（与整页模式对齐）：WebView 内部滚动量驱动头部向上折叠。
            // 只动布局高度不改渲染模式——不触碰 ADR-0007 的视口渲染与内存约束。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val visible = (placeable.height - headerScrollY).coerceAtLeast(0)
                        layout(placeable.width, visible) {
                            placeable.placeRelative(0, -headerScrollY)
                        }
                    },
            ) {
                ArticleHeader(
                    article = article,
                    aiSummaryState = aiSummaryState,
                    onGenerateSummary = onGenerateSummary,
                    translationUi = null,
                    onRetranslate = onRetranslate,
                    onShowOriginal = onShowOriginal,
                    onTranslationDisplayChange = onTranslationDisplayChange,
                    onTitleMeasured = onTitleMeasured,
                )
            }
            // 抓取结果横幅放在折叠区**之外**：viewport 模式下头部会随滚动折走，
            // 失败原因不能跟着一起消失。
            FetchStateBanner(
                state = contentFetchState,
                onRetry = onRetryFetch,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (resolvedPlan.summaryMode && translationUi == null) {
                SummaryModeBanner(
                    onShowFull = onShowFullContent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                translationSegments = translationSegments,
                plan = resolvedPlan,
                viewport = true,
                imageUrls = imageUrls,
                onHeaderScroll = onHeaderScroll,
                onImageClick = onImageClick,
                modifier = Modifier.weight(1f),
                find = find,
                onFindCount = onFindCount,
            )
            Spacer(Modifier.height(12.dp)) // 避让底部操作栏
        }
    } else {
        // 整页单滚动容器（用户反馈）：标题 / AI 摘要卡片随正文一起滚出，WebView 包内容高度
        Column(
            modifier = modifier
                .padding(vertical = 8.dp)
                .verticalScroll(scrollState),
        ) {
            ArticleHeader(
                article = article,
                aiSummaryState = aiSummaryState,
                onGenerateSummary = onGenerateSummary,
                translationUi = translationUi,
                onRetranslate = onRetranslate,
                onShowOriginal = onShowOriginal,
                onTranslationDisplayChange = onTranslationDisplayChange,
                onTitleMeasured = onTitleMeasured,
            )
            // 抓取结果（不完整 / 失败 + 重试）：抓到了什么、为什么没有，如实说。
            // 译文态下不显示——那时读者看的是译文，正文来源不是关注点。
            if (translationUi == null) {
                FetchStateBanner(
                    state = contentFetchState,
                    onRetry = onRetryFetch,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (resolvedPlan.summaryMode) {
                    SummaryModeBanner(
                        onShowFull = onShowFullContent,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            BodyContent(
                article = article,
                isFetchingContent = isFetchingContent,
                translationSegments = translationSegments,
                plan = resolvedPlan,
                viewport = false,
                imageUrls = imageUrls,
                onHeaderScroll = onHeaderScroll,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth(),
                find = find,
                onFindCount = onFindCount,
            )
            Spacer(Modifier.height(12.dp)) // 避让底部操作栏
        }
    }
}
