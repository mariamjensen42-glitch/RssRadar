package com.cycling.rssradar.ui.article

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
import com.cycling.rssradar.ui.components.openUrl
import com.cycling.rssradar.ui.theme.LocalReadingPrefs
import com.cycling.rssradar.core.ui.theme.radarColors

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
 */
@Composable
internal fun ArticleWebView(
    html: String,
    imageUrls: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    passThroughTouch: Boolean = true,
    onScroll: ((Int) -> Unit)? = null,
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
    // 关闭"点击放大"就传空集合：正文不包链接，图片点击在 WebView 里自然无反应。
    val linkedImages = if (image.maximizeOnTap) imageUrls.toSet() else emptySet()
    val styledHtml = remember(html, style, image, linkedImages, bg, fg, muted, codeBg, border, link) {
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
        )
    }
    // factory 只跑一次，回调经 updated 引用保持最新
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentImageUrls by rememberUpdatedState(linkedImages)
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
                    currentOnScroll?.invoke(t)
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
                // 一律不进 WebView 导航，http(s) 外链交系统浏览器（与"查看原文"一致），
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
            }
        },
        update = { webView ->
            if (lastLoaded[0] != styledHtml) {
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                lastLoaded[0] = styledHtml
            }
        },
        modifier = modifier,
    )
}

/** Compose Color → CSS #RRGGBB。 */
private fun toCssColor(color: androidx.compose.ui.graphics.Color): String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)

