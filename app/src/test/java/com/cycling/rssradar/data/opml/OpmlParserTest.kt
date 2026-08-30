package com.cycling.rssradar.data.opml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class OpmlParserTest {

    private fun parse(xml: String) = OpmlParser.parse(ByteArrayInputStream(xml.toByteArray()))

    @Test
    fun `parses flat outlines without folders`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
                <head><title>Subscriptions</title></head>
                <body>
                    <outline type="rss" text="Tech Blog" xmlUrl="https://example.com/rss.xml"/>
                    <outline type="rss" text="News" xmlUrl="https://example.org/feed"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parse(xml)

        assertEquals(2, entries.size)
        assertEquals(OpmlEntry(group = "", title = "Tech Blog", xmlUrl = "https://example.com/rss.xml"), entries[0])
        assertEquals(OpmlEntry(group = "", title = "News", xmlUrl = "https://example.org/feed"), entries[1])
    }

    @Test
    fun `maps one-level folders to groups`() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline text="技术">
                        <outline type="rss" text="Dev Blog" xmlUrl="https://example.com/dev.xml"/>
                    </outline>
                    <outline type="rss" text="Loose" xmlUrl="https://example.com/loose.xml"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parse(xml)

        assertEquals(2, entries.size)
        assertEquals("技术", entries[0].group)
        assertEquals("Dev Blog", entries[0].title)
        assertEquals("", entries[1].group)
    }

    @Test
    fun `joins nested folders with slash`() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline text="技术">
                        <outline text="后端">
                            <outline type="rss" text="Deep Feed" xmlUrl="https://example.com/deep.xml"/>
                        </outline>
                    </outline>
                </body>
            </opml>
        """.trimIndent()

        val entries = parse(xml)

        assertEquals(1, entries.size)
        assertEquals("技术/后端", entries[0].group)
    }

    @Test
    fun `falls back to title attribute and url for missing text`() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline type="rss" title="By Title Attr" xmlUrl="https://example.com/a.xml"/>
                    <outline type="rss" xmlUrl="https://example.com/b.xml"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parse(xml)

        assertEquals("By Title Attr", entries[0].title)
        assertEquals("https://example.com/b.xml", entries[1].title)
    }

    @Test
    fun `skips outlines without xmlUrl and feeds inside folders are kept`() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline text="empty folder"/>
                    <outline type="rss" text="Real" xmlUrl="https://example.com/real.xml"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parse(xml)

        assertEquals(1, entries.size)
        assertEquals("Real", entries[0].title)
    }

    @Test
    fun `throws on non-opml root`() {
        val xml = "<rss version=\"2.0\"><channel></channel></rss>"

        assertThrows(IllegalArgumentException::class.java) { parse(xml) }
    }
}
