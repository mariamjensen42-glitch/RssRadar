package com.cycling.rssradar.core.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 只测纯函数 [BestIconFinder.selectIconUrl] / [BestIconFinder.faviconUrl]（不联网）。
 * 网络链路由 FeedIcon 的字母块兜底覆盖，不值得 mock OkHttp 去测。
 */
class BestIconFinderTest {

    private val finder = BestIconFinder()

    @Test
    fun `prefers apple-touch-icon over rel icon`() {
        val html = """
            <html><head>
                <link rel="icon" href="/small.ico"/>
                <link rel="apple-touch-icon" href="/touch.png"/>
                <meta property="og:image" content="https://cdn.example.com/big.jpg"/>
            </head></html>
        """.trimIndent()

        val result = finder.selectIconUrl("https://site.example", html)

        assertEquals("https://site.example/touch.png", result)
    }

    @Test
    fun `matches shortcut icon as rel icon`() {
        val html = """<html><head>
            <link rel="shortcut icon" href="/favicon-32.png"/>
        </head></html>"""

        val result = finder.selectIconUrl("https://site.example", html)

        assertEquals("https://site.example/favicon-32.png", result)
    }

    @Test
    fun `resolves relative href against base url`() {
        val html = """<html><head>
            <link rel="icon" href="assets/icon.svg"/>
        </head></html>"""

        val result = finder.selectIconUrl("https://site.example/blog/", html)

        assertEquals("https://site.example/blog/assets/icon.svg", result)
    }

    // 反引号函数名原样成为 JVM 方法名，冒号是非法字符（Kotlin/JVM 直接编译报错）
    @Test
    fun `ignores og-image entirely`() {
        val html = """<html><head>
            <meta property="og:image" content="https://cdn.example.com/big.jpg"/>
        </head></html>"""

        val result = finder.selectIconUrl("https://site.example", html)

        assertNull(result)
    }

    @Test
    fun `returns null when no icon links`() {
        val html = """<html><head><title>nothing here</title></head></html>"""

        assertNull(finder.selectIconUrl("https://site.example", html))
    }

    @Test
    fun `builds favicon fallback url`() {
        assertEquals("https://site.example/favicon.ico", finder.faviconUrl("https://site.example"))
        assertEquals("https://site.example/favicon.ico", finder.faviconUrl("https://site.example/blog/"))
    }
}
