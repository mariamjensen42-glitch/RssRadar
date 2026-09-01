package com.cycling.rssradar.data.opml

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.InputStream

/** OPML 中的一条订阅源：分组路径 + 标题 + 订阅地址（+ 可选站点主页）。 */
data class OpmlEntry(
    /** 文件夹路径，多级用 `/` 拼接；空串表示无文件夹（归默认分组）。 */
    val group: String,
    /** OPML 的 text/title 属性；两者都缺省时回退为 xmlUrl。 */
    val title: String,
    val xmlUrl: String,
    /** 站点主页（htmlUrl 属性）：导入不用，导出回填，避免往返丢信息。 */
    val htmlUrl: String? = null,
)

/**
 * OPML 解析器（ADR-0004）：jsoup XML 模式解析，纯 JVM 组件，是导入链路的测试缝。
 *
 * - 根元素非 `<opml>` 抛 [IllegalArgumentException]，与 RssParser 的失败约定一致。
 * - 无 xmlUrl 的 outline 视为文件夹：其嵌套路径拼接为分组名（`技术/后端`）。
 * - 带 xmlUrl 的 outline 视为订阅源；解析不出 xmlUrl 的行自然被跳过。
 */
object OpmlParser {

    fun parse(input: InputStream): List<OpmlEntry> {
        val doc = Jsoup.parse(input, null, "", Parser.xmlParser())
        val root = doc.children().firstOrNull { it.normalName() == "opml" }
            ?: throw IllegalArgumentException("Not a valid OPML document")
        val entries = mutableListOf<OpmlEntry>()
        walk(root.selectFirst("body") ?: root, group = "", into = entries)
        return entries
    }

    private fun walk(el: Element, group: String, into: MutableList<OpmlEntry>) {
        el.children().forEach { child ->
            if (child.normalName() != "outline") return@forEach
            val name = child.attr("text").trim().ifBlank { child.attr("title").trim() }
            val xmlUrl = child.attr("xmlUrl").trim()
            if (xmlUrl.isNotEmpty()) {
                into += OpmlEntry(
                    group = group,
                    title = name.ifEmpty { xmlUrl },
                    xmlUrl = xmlUrl,
                    htmlUrl = child.attr("htmlUrl").trim().takeIf { it.isNotEmpty() },
                )
            } else {
                // 文件夹：子级分组路径向下累积
                walk(child, group = if (group.isEmpty()) name else "$group/$name", into = into)
            }
        }
    }
}
