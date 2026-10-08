package com.cycling.rssradar.core.data.parser

import net.dankito.readability4j.extended.processor.ArticleGrabberExtended
import net.dankito.readability4j.extended.util.RegExUtilExtended
import net.dankito.readability4j.model.ArticleGrabberOptions
import net.dankito.readability4j.model.ReadabilityOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 抓取节点的 class / style 契约。
 *
 * 库自带的 [ArticleGrabberExtended] 会给裸文本打上 `readability-styled` 与 `display: inline`，
 * 而本仓库的产物里**两样都不许出现**——[Readability.extract] 末尾的 `RssParser.sanitizeHtml`
 * 会剥掉全部 class（只留媒体占位卡）与不在白名单里的 style。这条契约原先靠一个抄来的
 * `RYArticleGrabberExtended` 覆写保证，现已改为只依赖净化层，所以断言要落在净化之后的产物上。
 *
 * 断言方式是「自己 vs 阳性对照」——阳性对照直接跑库的 grabber，证明它确实会打上这两样；
 * 少了这一步，库哪天不打了自己的断言也不会变红，等于没测。
 */
class ReadabilityTest {

    private fun longText(repeat: Int = 12): String =
        ("这是一段用于测试的中文正文，包含足够多的字符以通过完整性阈值。" +
            "真实文章通常会有多个段落，这里用重复文本来模拟足够的长度。").repeat(repeat)

    /**
     * 块级兄弟必须是 div：`<p>` 兄弟会走 hasSinglePInsideElement 分支（裸文本被整块替换、
     * 压根不进包裹分支），阳性对照就会失效。
     */
    private fun fixture(): String = """
        <html><body>
          <article>
            <div class="entry-content">
              裸文本段落：${longText()}
              <div>${longText()}</div>
            </div>
          </article>
        </body></html>
    """.trimIndent()

    @Test
    fun `extract strips the grabber's class and inline style`() {
        val content = Readability.extract("https://example.com/a", fixture())!!.contentHtml

        assertTrue("正文应保留", content.contains("裸文本段落"))
        assertFalse("产物不该带 readability-styled", content.contains("readability-styled"))
        assertFalse("产物不该带 display: inline", content.contains("display: inline"))
    }

    @Test
    fun `positive control - the library grabber does add both`() {
        val html = prepareWith(ArticleGrabberProbe(ReadabilityOptions(), RegExUtilExtended()), fixture())

        assertTrue("库 grabber 会打 readability-styled", html.contains("readability-styled"))
        assertTrue("库 grabber 会加 display: inline", html.contains("display: inline"))
    }

    private fun prepareWith(probe: Probe, html: String): String {
        val doc = Jsoup.parse(html, "https://example.com/c")
        probe.runPrepare(doc)

        return doc.html()
    }

    private interface Probe {
        fun runPrepare(doc: Document): List<Element>
    }

    private class ArticleGrabberProbe(options: ReadabilityOptions, regExExtended: RegExUtilExtended) :
        ArticleGrabberExtended(options, regExExtended), Probe {

        override fun runPrepare(doc: Document): List<Element> = prepareNodes(doc, ArticleGrabberOptions())
    }
}
