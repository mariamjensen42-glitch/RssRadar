package com.cycling.rssradar.ui.article

import androidx.compose.ui.res.stringResource


import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.cycling.rssradar.core.data.platform.openUrl
import com.cycling.rssradar.core.ui.theme.LocalReadingPrefs
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.setValue

/**
 * 页内查找在 WebView 路的状态。刻意**不用 Compose State**——[AndroidView] 的 update
 * 在组合期执行，往里写 State 会自激重组；这几个值只服务于"要不要再发一次 findNext"。
 */
private class WebViewFindState {
    var query = ""
    var ordinal = 0
    var count = 0
    var consumedCursor = -1
}

/** 阅读位置恢复的一次性闸门：同样不用 Compose State（理由见 [WebViewFindState]）。 */
private class WebViewRestoreState {
    var consumed = false
}

/**
 * 到顶 / 到底请求的已消费序号。按序号判重而不是按目标比值：连点两次「到底」时比值都是 1f，
 * 按比值判重会把第二次当成重复请求吞掉。
 */
private class WebViewJumpState {
    var consumedSeq = -1L
}

/**
 * 当前还能滚多少：内容总高（CSS px × 缩放）减去视口高。
 *
 * 不用 `computeVerticalScrollRange()/Extent()`——WebView 把它们标成了 protected，
 * 顶层扩展函数访问不到（只有子类内部能用）；[contentHeight] 与 [WebView.getScale] 都是公开的。
 * 页面未加载完时 contentHeight 为 0，结果是 0（调用方按「还滚不动」处理）。
 */
private fun WebView.scrollableRange(): Int =
    (contentHeight * scale).toInt().minus(height).coerceAtLeast(0)

/**
 * 按比例恢复视口模式的滚动位置。
 *
 * 幂等：每次都用**当前**可滚动上限重算——页面加载中与图片 reflow 之后上限都会变，
 * 隔一段时间多滚两次才能落到同一段文字上。调用点见 [ArticleWebView] 的 update。
 */
private fun WebView.restoreToRatio(ratio: Float) {
    val range = scrollableRange()
    if (range <= 0) return
    scrollTo(0, (ratio * range).toInt())
}

/**
 * 净化后的正文 HTML 用 WebView 渲染：排版参数与主题色注入 CSS（issue #42）。
 * 模板构建在 [ReadingContentHtml]（纯函数，JVM 单测覆盖）；本组合函数只负责
 * 从 radarColors() / LocalReadingPrefs 读实时值。
 *
 * [passThroughTouch]：整页模式（高度包内容）为 true——触摸穿透给外层 Compose 滚动，
 * 否则 WebView 会吞掉滑动手势；视口模式（有图文章，内部滚动）为 false——
 * WebView 必须自己消费触摸才能滚动。
 *
 * [onScroll]：视口模式头部折叠用，回调 WebView 内部滚动量（px）。
 *
 * [imageUrls]：本文图片地址（[ReadingImages.extract] 的结果）。"点击放大"开启时，
 * build 阶段会把 <img> 包成指向自身的 <a class="img-link">，于是点击图片和点击链接
 * 走同一条 shouldOverrideUrlLoading 通道——地址命中本集合就交给 [onImageClick]
 * （全屏查看），否则照旧开浏览器。**全程不开 JS**（ADR-0007 不动，详见 ADR-0011）。
 *
 * [findQuery]/[findCursor]/[onFindCount]：页内查找。用的是平台的 find-in-page
 * （`findAllAsync`/`findNext`/`clearMatches`），**同样不需要 JS**。
 * 已知边界：整页模式（高度包内容、由外层 Compose 滚动）时 WebView 自己滚不动，
 * 命中能高亮但「下一处」不会把视口带过去——这一条在查找栏里有如实说明。
 */
@Composable
internal fun ArticleWebView(
    html: String,
    imageUrls: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    passThroughTouch: Boolean = true,
    /** 视口模式的滚动上报：(滚动量, 可滚动上限)。上限为 0 = 内容不足一屏。 */
    onScroll: ((Int, Int) -> Unit)? = null,
    findQuery: String = "",
    findCursor: Int = 0,
    onFindCount: (Int) -> Unit = {},
    /** 视口模式要恢复的阅读位置（比例）。整页模式不传——那时 WebView 自己不滚动。 */
    restoreRatio: Float? = null,
    /** 到顶 / 到底的跳转请求。同样只对视口模式有意义：整页模式的滚动归外层 Compose。 */
    jumpRequest: JumpRequest? = null,
) {
    // 颜色读自 radarColors()（CompositionLocal），主题切换自动重组
    val bg = toCssColor(radarColors().bgRoot)
    val fg = toCssColor(radarColors().textPrimary)
    val muted = toCssColor(radarColors().textSecondary)
    val codeBg = toCssColor(radarColors().surface2)
    val border = toCssColor(radarColors().surface1)
    val link = toCssColor(radarColors().link)
    val style = LocalReadingPrefs.current.style
    val image = LocalReadingPrefs.current.image
    val immersive = LocalReadingPrefs.current.immersive
    // 关闭"点击放大"就传空集合：正文不包链接，图片点击在 WebView 里自然无反应。
    val linkedImages = if (image.maximizeOnTap) imageUrls.toSet() else emptySet()
    val styledHtml = remember(html, style, image, linkedImages, bg, fg, muted, codeBg, border, link, immersive) {
        ReadingContentHtml.build(
            contentHtml = html,
            style = style,
            bg = bg,
            fg = fg,
            muted = muted,
            codeBg = codeBg,
            border = border,
            link = link,
            imageUrls = linkedImages,
            imageCorners = image.cornerRadius,
            immersive = immersive,
        )
    }
    // factory 只跑一次，回调经 updated 引用保持最新
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentImageUrls by rememberUpdatedState(linkedImages)
    val currentOnFindCount by rememberUpdatedState(onFindCount)
    val findState = remember { WebViewFindState() }
    val restoreState = remember { WebViewRestoreState() }
    val jumpState = remember { WebViewJumpState() }
    // 闪烁修复（用户反馈）：AndroidView 的 update 在每次父重组时都会跑，而 ArticleWebView
    // 的父（ReadingBody）会因顶栏 showTitle 翻转而重组 → 不加守卫就会每帧 reload 整页 HTML。
    // 用非 State 容器记住"已加载的 HTML 串"，只有内容真变才 reload。
    val lastLoaded = remember { arrayOf<String?>(null) }
    AndroidView(
        factory = { context ->
            object : WebView(context) {
                override fun onTouchEvent(event: MotionEvent): Boolean =
                    if (passThroughTouch) false else super.onTouchEvent(event)

                override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                    super.onScrollChanged(l, t, oldl, oldt)
                    currentOnScroll?.invoke(t, scrollableRange())
                }

                // WebView 被布局移动后（首帧头部量测把它推到最终位置），Chromium 合成层
                // 不跟随移动——旧位置残影叠在 Compose 头部上，且 invalidate() 无效
                // （真机实证：dumpsys 里 View bounds 已正确，画面却停在旧 y；只有内部
                // 滚动能逼 Chromium 出新帧）。净零滚动 ±1px 强制合成器按新位置出帧。
                // 带图文章尤其严重：图片加载期 Chromium 首帧画得早（页面 reflow 中），
                // 落定后往往不再有布局回调，光靠 onLayout 触发不了——所以除了布局变化
                // 触发，还在加载完成和落定后延时补帧。scrollY==0 闸门：视口模式随滚折叠
                // 期间每帧都在改布局，不能每次都触发。
                fun forceFrame() {
                    if (canScrollVertically(1)) {
                        scrollBy(0, 1)
                        scrollBy(0, -1)
                    }
                    invalidate()
                }

                // 平台把 View.onLayout 标了 deprecated，但这里是刻意的 Chromium 残影
                // 兜底（见上注释），行为必须保留，压掉警告即可
                @Suppress("DEPRECATION")
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                    super.onLayout(changed, l, t, r, b)
                    if (changed && scrollY == 0) forceFrame()
                }

                override fun onAttachedToWindow() {
                    super.onAttachedToWindow()
                    // 图片加载造成的 reflow 在头几百毫秒到数秒内反复发生，落定时刻不确定；
                    // 分四个延时各补一帧兜底，幂等且 ±1px 净零滚动本身无害。
                    post { forceFrame() }
                    postDelayed({ forceFrame() }, 300)
                    postDelayed({ forceFrame() }, 800)
                    postDelayed({ forceFrame() }, 2000)
                }
            }.apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // 链接接管（视口模式生效；整页模式触摸穿透点不到，见 ADR-0007）：
                // 一律不进 WebView 导航，http(s) 外链交系统浏览器（与stringResource(R.string.view_original)一致），
                // 其余 scheme 静默丢弃——顺带消灭"原地导航把正文顶掉"的默认行为。
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        if (url in currentImageUrls) {
                            currentOnImageClick(url)
                            return true
                        }
                        if (request.url.scheme == "http" || request.url.scheme == "https") {
                            context.openUrl(url)
                        }
                        return true
                    }
                }
                // 平台自带 find-in-page：命中数由它算，我们只负责触发与上报（不开 JS）
                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    findState.ordinal = activeMatchOrdinal
                    findState.count = numberOfMatches
                    if (isDoneCounting) currentOnFindCount(numberOfMatches)
                }
            }
        },
        update = { webView ->
            if (lastLoaded[0] != styledHtml) {
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                lastLoaded[0] = styledHtml
            }
            // 页内查找：query 变了重发 findAllAsync；cursor 变了 findNext 一次。
            // consumedCursor 兜住「findNext 的结果异步回来之前 update 又被调一遍」的重复跳转。
            val query = findQuery.trim()
            when {
                query.isEmpty() -> {
                    if (findState.query.isNotEmpty()) {
                        findState.query = ""
                        findState.ordinal = 0
                        findState.count = 0
                        webView.clearMatches()
                        currentOnFindCount(0)
                    }
                }
                query != findState.query -> {
                    findState.query = query
                    // findAllAsync 落定即停在第一处，正好对应 cursor=0，不必再 findNext
                    findState.consumedCursor = findCursor
                    webView.findAllAsync(query)
                }
                findCursor != findState.consumedCursor -> {
                    findState.consumedCursor = findCursor
                    // ordinal 是 1 基，cursor 是 0 基：cursor >= ordinal 即往后走
                    webView.findNext(findCursor >= findState.ordinal)
                }
            }
            // 阅读位置恢复：只在首次触发（consumed 闸门），分三次重试覆盖首帧与图片
            // reflow 之后的新高度——那时可滚动上限变了，按新上限重算才落回同一段
            if (restoreRatio != null && !restoreState.consumed) {
                restoreState.consumed = true
                webView.post { webView.restoreToRatio(restoreRatio) }
                webView.postDelayed({ webView.restoreToRatio(restoreRatio) }, 300)
                webView.postDelayed({ webView.restoreToRatio(restoreRatio) }, 900)
            }
            // 到顶 / 到底：复用 restoreToRatio（它就是「滚到全文的某个比例」）。
            // 判重按 seq 而不是比值 —— 连点两次「到底」比值都是 1f，按比值判重会吞掉第二次。
            if (jumpRequest != null && jumpState.consumedSeq != jumpRequest.seq) {
                jumpState.consumedSeq = jumpRequest.seq
                webView.post { webView.restoreToRatio(jumpRequest.ratio) }
            }
        },
        modifier = modifier,
    )
}

/** Compose Color → CSS #RRGGBB。 */
private fun toCssColor(color: androidx.compose.ui.graphics.Color): String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)

