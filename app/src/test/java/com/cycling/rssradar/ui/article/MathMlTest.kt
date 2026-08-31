package com.cycling.rssradar.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MathMl] 的解析契约。
 *
 * 输入都是 sanitize 后的形态：属性已被剥光（class/xmlns 没了），只剩元素结构——
 * 这正是线上真实拿到的样子（探针实测 KaTeX/MathJax 的 `<math>` 都能活过 sanitize）。
 */
class MathMlTest {

    private fun parse(html: String): List<MathSpan> = MathMl.parse(html)

    private fun List<MathSpan>.joined(): String = joinToString("") { it.text }

    @Test
    fun `blank input yields nothing`() {
        assertEquals(emptyList<MathSpan>(), MathMl.parse(""))
        assertEquals(emptyList<MathSpan>(), MathMl.parse("<p>没有公式</p>"))
    }

    @Test
    fun `variables are italic while numbers and operators are not`() {
        val spans = parse("<math><mi>x</mi><mo>+</mo><mn>1</mn></math>")

        assertEquals(listOf(MathSpan("x", italic = true), MathSpan("+"), MathSpan("1")), spans)
    }

    @Test
    fun `msup and msub become super and subscript`() {
        val sup = parse("<math><msup><mi>x</mi><mn>2</mn></msup></math>")
        assertEquals(listOf(MathSpan("x", italic = true), MathSpan("2", script = MathScript.SUPER)), sup)

        val sub = parse("<math><msub><mi>a</mi><mn>1</mn></msub></math>")
        assertEquals(listOf(MathSpan("a", italic = true), MathSpan("1", script = MathScript.SUB)), sub)
    }

    @Test
    fun `msubsup emits base then sub then sup`() {
        val spans = parse("<math><msubsup><mi>x</mi><mn>1</mn><mn>2</mn></msubsup></math>")

        assertEquals(
            listOf(
                MathSpan("x", italic = true),
                MathSpan("1", script = MathScript.SUB),
                MathSpan("2", script = MathScript.SUPER),
            ),
            spans,
        )
    }

    @Test
    fun `simple fraction uses the fraction slash without parens`() {
        val spans = parse("<math><mfrac><mn>1</mn><mn>2</mn></mfrac></math>")

        assertEquals("1⁄2", spans.joined())
        assertEquals(3, spans.size)
        assertTrue(spans[1].text == "⁄")
    }

    @Test
    fun `complex fraction operands get parentheses`() {
        // 分子是结构化的（不是单个数字/变量）：要补括号避免 1+2⁄3 歧义
        val spans = parse(
            "<math><mfrac><mrow><mi>a</mi><mo>+</mo><mi>b</mi></mrow><mn>3</mn></mfrac></math>",
        )

        assertEquals("(a+b)⁄3", spans.joined())
    }

    @Test
    fun `square root wraps content in radical`() {
        val spans = parse("<math><msqrt><mrow><mi>x</mi><mo>+</mo><mn>1</mn></mrow></msqrt></math>")

        assertEquals("√(x+1)", spans.joined())
    }

    @Test
    fun `nth root puts the index as superscript before the radical`() {
        // MathML 的 mroot：第一个子元素是被开方数，第二个是根指数
        val spans = parse("<math><mroot><mi>x</mi><mn>3</mn></mroot></math>")

        assertEquals(listOf(MathSpan("3", script = MathScript.SUPER), MathSpan("√("), MathSpan("x", italic = true), MathSpan(")")), spans)
    }

    @Test
    fun `under and over attach as sub and super`() {
        val under = parse("<math><munder><mi>x</mi><mo>⏟</mo></munder></math>")
        assertEquals(MathScript.SUB, under.last().script)

        val over = parse("<math><mover><mi>x</mi><mo>^</mo></mover></math>")
        assertEquals(MathScript.SUPER, over.last().script)
    }

    @Test
    fun `table becomes rows separated by newlines`() {
        val spans = parse(
            "<math><mtable>" +
                "<mtr><mtd><mn>1</mn></mtd><mtd><mn>2</mn></mtd></mtr>" +
                "<mtr><mtd><mn>3</mn></mtd><mtd><mn>4</mn></mtd></mtr>" +
                "</mtable></math>",
        )

        assertEquals("1  2\n3  4", spans.joined())
    }

    @Test
    fun `phantom content is dropped`() {
        val spans = parse("<math><mphantom><mn>9</mn></mphantom><mn>1</mn></math>")

        assertEquals("1", spans.joined())
    }

    @Test
    fun `semantics keeps the mathml body and drops annotations`() {
        // MathJax/KaTeX 常见：annotation 里塞着 TeX 源码，不能让它跟正文重复出现
        val spans = parse(
            "<math><semantics><msup><mi>x</mi><mn>2</mn></msup>" +
                "<annotation encoding=\"application/x-tex\">x^2</annotation>" +
                "</semantics></math>",
        )

        assertEquals("x2", spans.joined())
        assertTrue(spans.none { it.text.contains("x^2") })
    }

    @Test
    fun `unknown tags fall back to their content`() {
        // sanitize 会剥属性但保结构；这里模拟一个不认识的容器，内容不能丢
        val spans = parse("<math><mxyzzy><mn>7</mn></mxyzzy></math>")

        assertEquals("7", spans.joined())
    }

    @Test
    fun `whitespace inside math is collapsed away`() {
        val spans = parse("<math>  <mi> x </mi>  <mo>+</mo> </math>")

        assertEquals("x+", spans.joined())
    }
}
