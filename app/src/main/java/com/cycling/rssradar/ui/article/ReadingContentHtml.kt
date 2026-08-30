package com.cycling.rssradar.ui.article

import com.cycling.rssradar.data.store.ReadingStyleState

/**
 * 阅读页 styled-HTML 构建（issue #42 单一测试缝）。
 *
 * 纯 JVM 函数：排版参数 + 实时主题色 + 净化后的正文 HTML → 完整可渲染文档。
 * 颜色由调用方从 RssRadarPalette 实时读出（#RRGGBB），本函数不做任何颜色决策；
 * 排版参数只在 CSS 中体现：font-size / line-height / padding / font-family。
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
    ): String = """
    <!DOCTYPE html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { background:$bg; color:$fg; font-size:${style.fontSize}px; line-height:${style.lineHeight};
               padding:0 ${style.horizontalPadding}px; margin:0; word-break:break-word;
               font-family:${style.fontFamily.cssStack}; }
        img { max-width:100%; height:auto; border-radius:8px; }
        a { color:$link; text-decoration:none; }
        p { margin:0 0 1em 0; }
        blockquote { margin:0 0 1em 0; padding:4px 12px; border-left:3px solid $border; color:$muted; }
        pre { background:$codeBg; padding:10px; border-radius:8px; overflow-x:auto; }
        code { font-family:Menlo,Consolas,monospace; font-size:13px; }
        h1,h2,h3 { line-height:1.4; }
        figure { margin:0 0 1em 0; }
    </style></head>
    <body>$contentHtml</body></html>
""".trimIndent()
}
