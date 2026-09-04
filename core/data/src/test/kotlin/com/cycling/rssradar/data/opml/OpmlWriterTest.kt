package com.cycling.rssradar.core.data.opml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * OPML 导出（[OpmlWriter]）单测：重点是"导入 → 导出 → 再导入"的往返不丢结构。
 * 纯 JVM，与 OpmlParserTest 对称。
 */
class OpmlWriterTest {

    @Test
    fun `write - emits valid opml skeleton`() {
        val xml = OpmlWriter.write(emptyList())
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("<head>"))
        assertTrue(xml.contains("<body>"))
        assertTrue(xml.trimEnd().endsWith("</opml>"))
    }

    @Test
    fun `write - flat feeds become top level outlines`() {
        val xml = OpmlWriter.write(
            listOf(
                OpmlEntry(group = "", title = "阮一峰", xmlUrl = "http://a/feed.xml"),
                OpmlEntry(group = "技术", title = "InfoQ", xmlUrl = "http://b/feed.xml"),
            ),
        )
        assertTrue(xml.contains("""<outline text="技术">"""))
        assertTrue(xml.contains("""type="rss" text="阮一峰" xmlUrl="http://a/feed.xml"/"""))
        assertTrue(xml.contains("""type="rss" text="InfoQ" xmlUrl="http://b/feed.xml"/"""))
    }

    @Test
    fun `write - nested group path becomes nested folders`() {
        val xml = OpmlWriter.write(
            listOf(OpmlEntry(group = "技术/后端", title = "美团技术团队", xmlUrl = "http://c/rss")),
        )
        val body = xml.substringAfter("<body>")
        assertTrue(body.contains("""<outline text="技术">"""))
        assertTrue(body.contains("""<outline text="后端">"""))
        // 顺序：先外层文件夹，再内层文件夹，最后才是源
        assertTrue(body.indexOf("""text="技术"""") < body.indexOf("""text="后端""""))
        assertTrue(body.indexOf("""text="后端"""") < body.indexOf("""xmlUrl="http://c/rss""""))
    }

    @Test
    fun `write - escapes xml special characters`() {
        val xml = OpmlWriter.write(
            listOf(OpmlEntry(group = "", title = "A & B \"quoted\" <tag>", xmlUrl = "http://d/feed?a=1&b=2")),
        )
        assertTrue(xml.contains("text=\"A &amp; B &quot;quoted&quot; &lt;tag&gt;\""))
        assertTrue(xml.contains("xmlUrl=\"http://d/feed?a=1&amp;b=2\""))
    }

    @Test
    fun `round trip - parse what was written keeps groups titles and urls`() {
        val original = listOf(
            OpmlEntry(group = "", title = "无分组源", xmlUrl = "http://x/1.xml"),
            OpmlEntry(group = "技术", title = "技术源", xmlUrl = "http://x/2.xml", htmlUrl = "http://x"),
            OpmlEntry(group = "技术/后端/Rust", title = "Rust 博客", xmlUrl = "http://x/3.xml"),
        )
        val reparsed = OpmlParser.parse(ByteArrayInputStream(OpmlWriter.write(original).toByteArray()))
        assertEquals(original.map { it.xmlUrl }.toSet(), reparsed.map { it.xmlUrl }.toSet())
        assertEquals(original.map { it.title }.toSet(), reparsed.map { it.title }.toSet())
        assertEquals(
            listOf("", "技术", "技术/后端/Rust").toSortedSet(),
            reparsed.map { it.group }.toSortedSet(),
        )
        assertEquals("http://x", reparsed.first { it.xmlUrl == "http://x/2.xml" }.htmlUrl)
    }
}
