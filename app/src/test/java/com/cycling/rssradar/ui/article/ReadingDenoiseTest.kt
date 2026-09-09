package com.cycling.rssradar.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReadingDenoise] 的降噪契约（纯 JVM：经 [ReadingNodes.parse] 构造输入）。
 *
 * 这里锁的都是降噪最容易翻车的形状：噪声段被剥掉、正文一字不动、
 * 链接列表整体删除、安全网在"清洗会丢掉一半正文"时果断放弃。
 */
class ReadingDenoiseTest {

    // ———————————————————————————————————————————————
    // 剥噪声
    // ———————————————————————————————————————————————

    @Test
    fun `link only short paragraph is dropped`() {
        val nodes = ReadingNodes.parse(
            "<p>这是正文第一段，讲的是一个完整的观点。</p>" +
                "<p><a href=\"https://example.com\">阅读原文</a></p>",
        )
        val cleaned = ReadingDenoise.clean(nodes)
        assertEquals(1, cleaned.size)
        assertTrue(cleaned.joinToString("") { it.plainText() }.contains("正文第一段"))
    }

    @Test
    fun `noise keyword paragraph is dropped`() {
        val nodes = ReadingNodes.parse(
            "<p>这是一段足够长的正文内容，用来保证安全网不会触发回退行为。</p>" +
                "<p>责任编辑：王小明</p>" +
                "<p>扫码关注公众号获取更多精彩内容</p>",
        )
        val cleaned = ReadingDenoise.clean(nodes)
        assertEquals(1, cleaned.size)
    }

    @Test
    fun `standalone ad label line is dropped`() {
        val nodes = ReadingNodes.parse(
            "<p>这是一段足够长的正文内容，用来保证安全网不会触发回退行为。</p>" +
                "<p>广告</p>",
        )
        val cleaned = ReadingDenoise.clean(nodes)
        assertEquals(1, cleaned.size)
    }

    @Test
    fun `link list is dropped as navigation`() {
        val nodes = ReadingNodes.parse(
            "<p>这是一段足够长的正文内容，用来保证安全网不会触发回退行为。</p>" +
                "<ul>" +
                "<li><a href=\"https://a.com\">相关文章一</a></li>" +
                "<li><a href=\"https://b.com\">相关文章二</a></li>" +
                "<li><a href=\"https://c.com\">相关文章三</a></li>" +
                "</ul>",
        )
        val cleaned = ReadingDenoise.clean(nodes)
        assertEquals(1, cleaned.size)
    }

    @Test
    fun `noise heading is dropped but body stays`() {
        val nodes = ReadingNodes.parse(
            "<h3>推荐阅读</h3>" +
                "<p>这是一段足够长的正文内容，用来保证安全网不会触发回退行为。</p>",
        )
        val cleaned = ReadingDenoise.clean(nodes)
        assertTrue(cleaned.none { it is NodeHeading })
        assertTrue(cleaned.any { it is NodeParagraph })
    }

    // ———————————————————————————————————————————————
    // 保正文
    // ———————————————————————————————————————————————

    @Test
    fun `regular content is untouched`() {
        val html = "<h2>标题</h2><p>第一段正文，内容完整。</p>" +
            "<ul><li>要点一</li><li>要点二</li></ul>" +
            "<blockquote>引用一句话</blockquote>" +
            "<pre>val code = 1</pre>"
        val nodes = ReadingNodes.parse(html)
        assertEquals(nodes, ReadingDenoise.clean(nodes))
    }

    @Test
    fun `paragraph containing link but mostly text is kept`() {
        val html = "<p>更详细的背景资料可以在<a href=\"https://example.com\">官方文档</a>里查到，这里不再展开。</p>"
        val nodes = ReadingNodes.parse(html)
        assertEquals(nodes, ReadingDenoise.clean(nodes))
    }

    @Test
    fun `single item link list is kept`() {
        val html = "<ul><li><a href=\"https://example.com\">参考来源</a></li></ul>"
        val nodes = ReadingNodes.parse(html)
        assertEquals(nodes, ReadingDenoise.clean(nodes))
    }

    @Test
    fun `paragraph over sixty chars is never dropped`() {
        val long = "这句正文超过了六十个字符的噪声判定上限，因此无论它包含什么关键词" +
            "——哪怕是责任编辑这样的字样——也绝不会被降噪逻辑误删，这是规则一的前提。"
        val html = "<p>$long</p>"
        val nodes = ReadingNodes.parse(html)
        assertEquals(nodes, ReadingDenoise.clean(nodes))
    }

    // ———————————————————————————————————————————————
    // 安全网
    // ———————————————————————————————————————————————

    @Test
    fun `safety net restores original when cleaning loses half the body`() {
        // 链接短段占正文大头（清洗后只剩一小段）→ 丢失过半，安全网放弃清洗，原样返回
        val links = (1..16).joinToString("") {
            "<p><a href=\"https://e.com/$it\">这是第${it}条看起来像链接的短正文</a></p>"
        }
        val body = "<p>唯一幸存的短段落。</p>"
        val nodes = ReadingNodes.parse(links + body)
        assertEquals(nodes, ReadingDenoise.clean(nodes))
    }

    @Test
    fun `empty input stays empty`() {
        assertTrue(ReadingDenoise.clean(emptyList()).isEmpty())
    }

    /** 与 ReadingNodesTest 的同名助手一致：扁平化取纯文本。 */
    private fun ReadingNode.plainText(): String = when (this) {
        is NodeParagraph -> runs.joinToString("") { it.text }
        is NodeHeading -> runs.joinToString("") { it.text }
        is NodeList -> items.joinToString("") { it.runs.joinToString("") { r -> r.text } }
        is NodeQuote -> blocks.joinToString("") { it.plainText() }
        is NodeGroup -> nodes.joinToString("") { it.plainText() }
        is NodeDetails -> (summaryRuns.orEmpty().joinToString("") { it.text }) +
            blocks.joinToString("") { it.plainText() }
        is NodeCaption -> runs.joinToString("") { it.text }
        else -> ""
    }
}
