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
        a { color:$link; text-decoration:underline; text-underline-offset:2px; }
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
    </style></head>
    <body>$contentHtml</body></html>
""".trimIndent()
}
