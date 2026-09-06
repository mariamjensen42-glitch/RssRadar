package com.cycling.rssradar.ui.article

import com.cycling.rssradar.core.data.store.ReadingImageState
import com.cycling.rssradar.core.data.store.ReadingStyleState

/**
 * 阅读页 styled-HTML 构建（issue #42 单一测试缝）。
 *
 * 纯 JVM 函数：排版参数 + 实时主题色 + 净化后的正文 HTML → 完整可渲染文档。
 * 颜色由调用方从 radarColors() 实时读出（#RRGGBB），本函数不做任何颜色决策；
 * 排版参数只在 CSS 中体现：font-size / line-height / padding / font-family。
 *
 * [imageUrls] 是本文的图片地址集合（[ReadingImages.extract] 的产物）：非空时每张图会被
 * 包成指向自身的链接，让 JS 禁用的 WebView 也能把点图当点链接上报（ADR-0011）；
 * 传空集合 = 点击放关闭，正文原样输出。调用方拿同一份集合做点击分流与全屏翻页。
 * [imageCorners] 是图片圆角 dp，默认与引入该设置前的 8px 一致。
 */
object ReadingContentHtml {

    fun build(
        contentHtml: String,
        style: ReadingStyleState,
        bg: String,
        fg: String,
        muted: String,
        codeBg: String,
        border: String,
        link: String,
        imageUrls: Set<String> = emptySet(),
        imageCorners: Int = ReadingImageState.DEFAULT_CORNER_RADIUS,
        /** 沉浸阅读（issue #93）：注入降噪 CSS，隐藏分享/推荐/评论区等杂乱元素。 */
        immersive: Boolean = false,
    ): String {
        val body = if (imageUrls.isEmpty()) {
            contentHtml
        } else {
            ReadingImages.wrapForMaximize(contentHtml, imageUrls)
        }
        return """
    <!DOCTYPE html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { background:$bg; color:$fg; font-size:${style.fontSize}px; line-height:${style.lineHeight};
               padding:0 ${style.horizontalPadding}px; margin:0; word-break:break-word;
               font-family:${style.fontFamily.cssStack}; }
        img { max-width:100%; height:auto; border-radius:${imageCorners}px; }
        a { color:$link; text-decoration:underline; text-underline-offset:2px; }
        a.${ReadingImages.IMG_LINK_CLASS} { text-decoration:none; color:inherit; }
        p { margin:0 0 1em 0; }
        h1,h2,h3,h4,h5,h6 { line-height:1.4; margin:1.4em 0 0.5em 0; font-weight:700; }
        h1 { font-size:1.45em; }
        h2 { font-size:1.28em; }
        h3 { font-size:1.12em; }
        h4,h5,h6 { font-size:1em; }
        ul,ol { margin:0 0 1em 0; padding-left:1.5em; }
        li { margin:0 0 0.4em 0; }
        blockquote { margin:0 0 1em 0; padding:4px 12px; border-left:3px solid $border; color:$muted; }
        pre { background:$codeBg; padding:10px; border-radius:8px; overflow-x:auto; }
        code { font-family:Menlo,Consolas,monospace; font-size:0.9em; }
        :not(pre) > code { background:$codeBg; padding:2px 5px; border-radius:4px; }
        pre code { background:none; padding:0; border-radius:0; }
        table { display:block; width:fit-content; max-width:100%; overflow-x:auto; border-collapse:collapse; margin:0 0 1em 0; font-size:0.92em; }
        th,td { border:1px solid $border; padding:6px 10px; text-align:left; vertical-align:top; }
        th { background:$codeBg; font-weight:600; }
        hr { border:none; border-top:1px solid $border; margin:1.6em 0; }
        figure { margin:0 0 1em 0; }
        figcaption { text-align:center; font-size:0.85em; color:$muted; margin-top:6px; }
        del { color:$muted; }
        sup,sub { line-height:0; }
        .media-card { display:flex; align-items:center; gap:8px; background:$codeBg; border:1px solid $border;
                      border-radius:8px; padding:12px; margin:0 0 1em 0; color:$fg; text-decoration:none; font-size:0.9em; }
        .media-card span { color:$link; }
        ${if (immersive) DENOISE_CSS else ""}
    </style></head>
    <body>$body</body></html>
""".trimIndent()
    }

    /**
     * 降噪 CSS（沉浸阅读，issue #93）：WebView 路拿不到中间树，只能按选择器隐藏。
     * 与 [com.cycling.rssradar.core.data.parser.ArticleExtractor] 的噪声选择器同一批
     * 目标（导航/分享/推荐/评论/广告位）；只 display:none 不删节点，正文零风险。
     */
    private const val DENOISE_CSS = """
        nav, aside, form, footer, [role=navigation], [role=complementary], [role=search],
        .share, .sharing, .social, .social-share, .share-buttons, .shareto,
        .related, .related-posts, .related-read, .recommend, .recommended, .reads,
        .comment, .comments, #comments, .comment-box, #disqus_thread,
        .breadcrumb, .breadcrumbs, .pagination, .pager, .page-nav,
        .newsletter, .subscribe-box, .promo, .sponsor, .sponsored, .advert, .ads,
        .sidebar, .widget, .author-bio, .copyright, .topbar, .toolbar,
        .ds-like, .zan, .praise, [class*=advert], [class*=sponsor], [id*=advert],
        ins.adsbygoogle { display:none !important; }
    """
}
