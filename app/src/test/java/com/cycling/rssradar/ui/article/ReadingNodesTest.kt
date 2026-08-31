package com.cycling.rssradar.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReadingNodes] 的解析契约（纯 JVM：只依赖 jsoup，不碰 Android 与 Compose）。
 *
 * 这里锁的都是旧实现踩过的坑：片段间空格被 trim 掉、嵌套列表被压平、多行代码塌成一行、
 * 嵌套表格的行串进外层表、空 src 图片渲染成空占位、深层嵌套打爆栈。
 */
class ReadingNodesTest {

    // ———————————————————————————————————————————————
    // 空输入与兜底
    // ———————————————————————————————————————————————

    @Test
    fun `blank html yields no nodes`() {
        assertEquals(emptyList<ReadingNode>(), ReadingNodes.parse(""))
        assertEquals(emptyList<ReadingNode>(), ReadingNodes.parse("   \n  "))
    }

    @Test
    fun `whitespace only markup yields no nodes`() {
        assertEquals(0, ReadingNodes.parse("<p></p><p>   </p><div><br></div>").size)
    }

    @Test
    fun `unclosed tags do not break parsing`() {
        val nodes = ReadingNodes.parse("<p>hello<div><b>bold")
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.joinToString("") { it.plainText() }.contains("hello"))
    }

    // ———————————————————————————————————————————————
    // 行内：空白 / 样式 / 链接
    // ———————————————————————————————————————————————

    @Test
    fun `spaces between inline runs are preserved`() {
        val nodes = ReadingNodes.parse("<p>Hello <b>world</b>!</p>")
        assertEquals(1, nodes.size)
        // 逐段 trim 的版本会拼出 "Helloworld!"——这是本次修复的主 bug
        assertEquals("Hello world!", (nodes.single() as NodeParagraph).runs.join())
    }

    @Test
    fun `surrounding whitespace is trimmed once per block`() {
        val runs = (ReadingNodes.parse("<p>  \n  spaced  \n </p>").single() as NodeParagraph).runs
        assertEquals("spaced", runs.join())
    }

    @Test
    fun `inline style flags are captured`() {
        val runs = (ReadingNodes.parse("<p><b>b</b><i>i</i><code>c</code><del>d</del></p>")
            .single() as NodeParagraph).runs
        assertEquals(
            listOf(
                InlineText("b", bold = true),
                InlineText("i", italic = true),
                InlineText("c", code = true),
                InlineText("d", strike = true),
            ),
            runs,
        )
    }

    @Test
    fun `line break becomes a newline run`() {
        val runs = (ReadingNodes.parse("<p>a<br>b</p>").single() as NodeParagraph).runs
        assertEquals("a\nb", runs.join())
    }

    @Test
    fun `http link becomes a link run`() {
        val runs = (ReadingNodes.parse("""<p>see <a href="https://a.com/x">here</a></p>""")
            .single() as NodeParagraph).runs
        assertEquals(listOf(InlineText("see "), InlineLink("here", "https://a.com/x")), runs)
    }

    @Test
    fun `non http link degrades to plain text`() {
        val runs = (ReadingNodes.parse("""<p><a href="mailto:a@b.com">mail</a></p>""")
            .single() as NodeParagraph).runs
        assertEquals(1, runs.size)
        assertEquals("mail", (runs.single() as InlineText).text)
    }

    @Test
    fun `protocol relative link is upgraded to https`() {
        val runs = (ReadingNodes.parse("""<p><a href="//a.com/x">go</a></p>""")
            .single() as NodeParagraph).runs
        assertEquals(InlineLink("go", "https://a.com/x"), runs.single())
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("A & B", (ReadingNodes.parse("<p>A &amp; B</p>").single() as NodeParagraph).runs.join())
    }

    // ———————————————————————————————————————————————
    // 块级：标题 / 列表 / 引用 / 代码 / 分隔线
    // ———————————————————————————————————————————————

    @Test
    fun `heading level is parsed`() {
        assertEquals(1, (ReadingNodes.parse("<h1>a</h1>").single() as NodeHeading).level)
        assertEquals(6, (ReadingNodes.parse("<h6>a</h6>").single() as NodeHeading).level)
        assertEquals(0, ReadingNodes.parse("<h1></h1>").size)
    }

    @Test
    fun `nested list is kept as a nested node`() {
        val list = ReadingNodes.parse("<ul><li>a<ul><li>b</li></ul></li></ul>").single() as NodeList
        assertEquals(1, list.items.size)
        assertEquals("a", list.items[0].runs.join())
        val nested = list.items[0].nested
        assertTrue("嵌套列表应保留为 NodeList，旧实现被压成同一段文本", nested != null)
        assertEquals(listOf("b"), nested!!.items.map { it.runs.join() })
    }

    @Test
    fun `ordered flag distinguishes ol from ul`() {
        assertTrue((ReadingNodes.parse("<ol><li>a</li></ol>").single() as NodeList).ordered)
        assertTrue(!(ReadingNodes.parse("<ul><li>a</li></ul>").single() as NodeList).ordered)
    }

    @Test
    fun `empty list items are skipped`() {
        val list = ReadingNodes.parse("<ul><li></li><li>a</li></ul>").single() as NodeList
        assertEquals(1, list.items.size)
    }

    @Test
    fun `blockquote keeps its paragraphs apart`() {
        val quote = ReadingNodes.parse("<blockquote><p>one</p><p>two</p></blockquote>").single() as NodeQuote
        assertEquals(2, quote.blocks.size)
        assertEquals(listOf("one", "two"), quote.blocks.map { it.plainText() })
    }

    @Test
    fun `pre keeps newlines and indentation`() {
        val code = ReadingNodes.parse("<pre>line1\nline2\n  indented</pre>").single() as NodeCode
        // jsoup 的 text() 会归一化空白（旧实现把多行代码塌成一行），wholeText 才保留
        assertEquals("line1\nline2\n  indented", code.code)
    }

    @Test
    fun `horizontal rule is a rule node`() {
        assertEquals(NodeRule, ReadingNodes.parse("<hr>").single())
    }

    // ———————————————————————————————————————————————
    // 表格
    // ———————————————————————————————————————————————

    @Test
    fun `nested table rows do not leak into the outer table`() {
        val html = "<table><tr><td>a<table><tr><td>nested</td></tr></table></td></tr></table>"
        val table = ReadingNodes.parse(html).single() as NodeTable
        assertEquals(1, table.rows.size)
        assertEquals("a", table.rows[0].cells[0].join())
    }

    @Test
    fun `thead rows are marked as header`() {
        val html = "<table><thead><tr><th>h</th></tr></thead><tbody><tr><td>d</td></tr></tbody></table>"
        val table = ReadingNodes.parse(html).single() as NodeTable
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0].isHeader)
        assertTrue(!table.rows[1].isHeader)
    }

    @Test
    fun `table without rows yields no node`() {
        assertEquals(0, ReadingNodes.parse("<table><tbody></tbody></table>").size)
    }

    // ———————————————————————————————————————————————
    // 图片与嵌入
    // ———————————————————————————————————————————————

    @Test
    fun `image without a resolvable src is dropped`() {
        assertEquals(0, ReadingNodes.parse("""<img src="">""").size)
        assertEquals(0, ReadingNodes.parse("""<img src="/relative/a.png">""").size)
    }

    @Test
    fun `protocol relative image src is upgraded to https`() {
        val img = ReadingNodes.parse("""<img src="//a.com/x.png">""").single() as NodeImage
        assertEquals("https://a.com/x.png", img.src)
    }

    @Test
    fun `image inside a link keeps the link`() {
        val img = ReadingNodes.parse("""<div><a href="https://a.com/page"><img src="https://a.com/x.png"></a></div>""")
            .single() as NodeImage
        assertEquals("https://a.com/x.png", img.src)
        assertEquals("https://a.com/page", img.href)
    }

    @Test
    fun `media card label drops the play glyph`() {
        val card = ReadingNodes.parse(
            """<a class="media-card" href="https://youtu.be/abc"><span>▶</span>嵌入内容 · youtu.be</a>""",
        ).single() as NodeMediaCard
        assertEquals("https://youtu.be/abc", card.url)
        assertEquals("嵌入内容 · youtu.be", card.label)
    }

    @Test
    fun `media card without a usable href is dropped`() {
        assertEquals(0, ReadingNodes.parse("""<a class="media-card">▶嵌入内容</a>""").size)
    }

    @Test
    fun `leftover iframe degrades to a media card`() {
        val card = ReadingNodes.parse("""<iframe src="//player.bilibili.com/1"></iframe>""")
            .single() as NodeMediaCard
        assertEquals("https://player.bilibili.com/1", card.url)
        assertEquals("嵌入内容 · player.bilibili.com", card.label)
    }

    @Test
    fun `leftover video without src is dropped`() {
        assertEquals(0, ReadingNodes.parse("<video></video>").size)
    }

    // ———————————————————————————————————————————————
    // 结构
    // ———————————————————————————————————————————————

    @Test
    fun `mixed text and block in one div splits into blocks`() {
        val group = ReadingNodes.parse("<div>intro<p>para</p></div>").single() as NodeGroup
        assertEquals(2, group.nodes.size)
        assertEquals("intro", group.nodes[0].plainText())
    }

    @Test
    fun `figure merges img and figcaption into captioned image`() {
        val node = ReadingNodes.parse("""<figure><img src="https://a.com/x.png"><figcaption>cap</figcaption></figure>""")
            .single() as NodeImage
        assertEquals("cap", node.caption)
    }

    @Test
    fun `single child group is flattened`() {
        val nodes = ReadingNodes.parse("<div><p>only</p></div>")
        assertEquals(1, nodes.size)
        assertTrue(nodes.single() is NodeParagraph)
    }

    @Test
    fun `script content never reaches the tree`() {
        assertTrue(ReadingNodes.parse("<p>text</p><script>alert(1)</script>").none { it.plainText().contains("alert") })
    }

    // ———————————————————————————————————————————————
    // 防御：深度 / 总量
    // ———————————————————————————————————————————————

    @Test
    fun `deeply nested markup does not blow the stack`() {
        val html = "<div>".repeat(5000) + "x" + "</div>".repeat(5000)
        // 结论不设断言：能跑完（不抛 StackOverflowError）就是过
        ReadingNodes.parse(html)
    }

    @Test
    fun `block count is capped`() {
        val html = "<p>x</p>".repeat(2000)
        assertTrue(ReadingNodes.parse(html).size <= ReadingNodes.MAX_BLOCKS)
    }

    // ———————————————————————————————————————————————
    // URL 归一化
    // ———————————————————————————————————————————————

    @Test
    fun `absoluteUrl only accepts http schemes`() {
        assertEquals("https://a.com/x", ReadingNodes.absoluteUrl("https://a.com/x"))
        assertEquals("http://a.com/x", ReadingNodes.absoluteUrl("http://a.com/x"))
        assertEquals("https://a.com/x", ReadingNodes.absoluteUrl("//a.com/x"))
        assertEquals(null, ReadingNodes.absoluteUrl(""))
        assertEquals(null, ReadingNodes.absoluteUrl("/relative"))
        assertEquals(null, ReadingNodes.absoluteUrl("javascript:alert(1)"))
        assertEquals(null, ReadingNodes.absoluteUrl(null))
    }

    // ———————————————————————————————————————————————
    // 上下标与公式
    // ———————————————————————————————————————————————

    @Test
    fun `sup and sub capture script position`() {
        val sup = (ReadingNodes.parse("<p>x<sup>2</sup></p>").single() as NodeParagraph).runs
        assertEquals(listOf(InlineText("x"), InlineText("2", script = MathScript.SUPER)), sup)

        val sub = (ReadingNodes.parse("<p>H<sub>2</sub>O</p>").single() as NodeParagraph).runs
        assertEquals(listOf(InlineText("H"), InlineText("2", script = MathScript.SUB), InlineText("O")), sub)
    }

    @Test
    fun `math inside a paragraph stays inline`() {
        val para = ReadingNodes.parse(
            "<p>公式 <math><msup><mi>x</mi><mn>2</mn></msup></math> 结束</p>",
        ).single() as NodeParagraph

        val math = para.runs.filterIsInstance<InlineMath>().single()
        assertEquals("x2", math.spans.joinToString("") { it.text })
        assertTrue(para.runs.first() is InlineText)
    }

    @Test
    fun `paragraph of only math becomes a block`() {
        val node = ReadingNodes.parse(
            "<p><math><mfrac><mn>1</mn><mn>2</mn></mfrac></math></p>",
        ).single()

        assertTrue("整段只有公式时应升级为块级公式", node is NodeMath)
        assertEquals("1⁄2", (node as NodeMath).spans.joinToString("") { it.text })
    }

    @Test
    fun `math as direct child of a block container becomes a block`() {
        val node = ReadingNodes.parse(
            "<div><math><mi>a</mi><mo>+</mo><mi>b</mi></math></div>",
        ).single()

        assertTrue(node is NodeMath)
    }

    @Test
    fun `latex cdn image is marked as formula`() {
        val formula = ReadingNodes.parse(
            """<img src="https://latex.codecogs.com/svg.latex?E%3Dmc%5E2" alt="E=mc^2">""",
        ).single() as NodeImage
        assertTrue("公式图要被标记（深色主题垫底色用）", formula.isFormula)

        val altOnly = ReadingNodes.parse(
            """<img src="https://blog.example.com/img/formula.png" alt="\fracrac{a}{b}">""",
        ).single() as NodeImage
        assertTrue(altOnly.isFormula)

        val normal = ReadingNodes.parse(
            """<img src="https://blog.example.com/photo.jpg" alt="一张风景照">""",
        ).single() as NodeImage
        assertTrue("普通图不能误标", !normal.isFormula)
    }

    // ———————————————————————————————————————————————
    // 行内样式扩展：u/mark/small/q + style 属性
    // ———————————————————————————————————————————————

    @Test
    fun `underline mark small q tags are captured`() {
        val runs = (ReadingNodes.parse("<p><u>u</u><mark>m</mark><small>s</small><q>q</q></p>")
            .single() as NodeParagraph).runs
        assertEquals(
            listOf(
                InlineText("u", underline = true),
                InlineText("m", mark = true),
                InlineText("s", small = true),
                InlineText("q", italic = true),
            ),
            runs,
        )
    }

    @Test
    fun `style color and decorations are applied`() {
        val runs = (ReadingNodes.parse(
            """<p><span style="color:#ff0000">红</span>""" +
                """<span style="text-decoration:underline">线</span>""" +
                """<span style="background-color: yellow">亮</span></p>""",
        ).single() as NodeParagraph).runs
        assertEquals(3, runs.size)
        assertTrue((runs[0] as InlineText).color != null)
        assertTrue((runs[1] as InlineText).underline)
        assertTrue((runs[2] as InlineText).mark)
    }

    @Test
    fun `unsafe style values are ignored`() {
        val runs = (ReadingNodes.parse(
            """<p><span style="color:url(https://evil.example.com/x)">a</span>""" +
                """<span style="position:fixed">b</span>""" +
                """<span style="color:expression(alert(1))">c</span></p>""",
        ).single() as NodeParagraph).runs
        // 三个 span 都退化成无样式纯文本
        assertEquals(
            listOf(InlineText("a"), InlineText("b"), InlineText("c")),
            runs,
        )
    }

    @Test
    fun `nested inline styles stack`() {
        val runs = (ReadingNodes.parse("<p><b><u>bu</u></b></p>").single() as NodeParagraph).runs
        assertEquals(listOf(InlineText("bu", bold = true, underline = true)), runs)
    }

    // ———————————————————————————————————————————————
    // 块级扩展：dl / figure / details / 表格 caption / 对齐
    // ———————————————————————————————————————————————

    @Test
    fun `definition list becomes term-description pairs`() {
        val node = ReadingNodes.parse(
            "<dl><dt>RSS</dt><dd>RDF Site Summary</dd><dt>Atom</dt></dl>",
        ).single() as NodeDefList
        assertEquals(2, node.items.size)
        assertEquals("RSS", node.items[0].termRuns.join())
        assertEquals("RDF Site Summary", node.items[0].descRuns.join())
        // 无 dd 的 dt 保留为纯术语条目
        assertEquals("Atom", node.items[1].termRuns.join())
        assertEquals(0, node.items[1].descRuns.size)
    }

    @Test
    fun `figure with img and figcaption yields caption on image`() {
        val node = ReadingNodes.parse(
            """<figure><img src="https://a.example.com/x.jpg" alt="a">""" +
                "<figcaption>图一：示意</figcaption></figure>",
        ).single() as NodeImage
        assertEquals("图一：示意", node.caption)
    }

    @Test
    fun `figure without img keeps figcaption as caption node`() {
        val node = ReadingNodes.parse(
            "<figure><figcaption>独立说明</figcaption></figure>",
        ).single() as NodeCaption
        assertEquals("独立说明", node.runs.join())
    }

    @Test
    fun `table caption is extracted`() {
        val node = ReadingNodes.parse(
            "<table><caption>统计</caption><tr><th>k</th></tr><tr><td>1</td></tr></table>",
        ).single() as NodeTable
        assertEquals("统计", node.caption)
        assertEquals(2, node.rows.size)
    }

    @Test
    fun `details becomes collapsible node with summary`() {
        val node = ReadingNodes.parse(
            "<details><summary>展开看看</summary><p>内容</p></details>",
        ).single() as NodeDetails
        assertEquals("展开看看", node.summaryRuns?.join())
        assertEquals("内容", node.blocks.single().plainText())
    }

    @Test
    fun `paragraph text-align is captured`() {
        val node = ReadingNodes.parse(
            """<p style="text-align:center">居中</p>""",
        ).single() as NodeParagraph
        assertEquals(ParagraphAlign.CENTER, node.align)
    }

    // ———————————————————————————————————————————————
    // 辅助
    // ———————————————————————————————————————————————

    private fun List<InlineRun>.join(): String = joinToString("") { it.text }

    private fun ReadingNode.plainText(): String = when (this) {
        is NodeParagraph -> runs.join()
        is NodeHeading -> runs.join()
        is NodeList -> items.joinToString(" ") { it.runs.join() + (it.nested?.plainText() ?: "") }
        is NodeQuote -> blocks.joinToString(" ") { it.plainText() }
        is NodeCode -> code
        is NodeImage -> alt.orEmpty()
        is NodeMediaCard -> label
        is NodeTable -> rows.joinToString(" ") { row -> row.cells.joinToString(" ") { it.join() } }
        is NodeGroup -> nodes.joinToString(" ") { it.plainText() }
        is NodeMath -> spans.joinToString("") { it.text }
        is NodeDefList -> items.joinToString(" ") { it.termRuns.join() + " " + it.descRuns.join() }
        is NodeCaption -> runs.join()
        is NodeDetails -> blocks.joinToString(" ") { it.plainText() }
        NodeRule -> ""
    }
}
