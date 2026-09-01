package com.cycling.rssradar.data.opml

/**
 * OPML 序列化（导出）：把订阅源清单写成标准 OPML 2.0，与 [OpmlParser] 对称。
 *
 * 分组还原：OPML 的文件夹是嵌套 outline，而库里存的是扁平 groupName
 * （导入时把嵌套路径拼成 `技术/后端`）。导出时按 `/` 切开重建嵌套结构，
 * 因此「导入别人的 OPML → 导出」能拿回等同的层级。
 *
 * 纯 JVM 组件（无 Android 依赖），是导出链路的测试缝。
 */
object OpmlWriter {

    /** 文档标题（`<title>` 节点），供其他阅读器显示来源。 */
    private const val DEFAULT_TITLE = "RssRadar 订阅"

    fun write(entries: List<OpmlEntry>, title: String = DEFAULT_TITLE): String {
        val root = Node(name = "", children = mutableMapOf(), feeds = mutableListOf())
        for (entry in entries) {
            val segments = entry.group.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            var cursor = root
            for (segment in segments) {
                cursor = cursor.children.getOrPut(segment) {
                    Node(name = segment, children = mutableMapOf(), feeds = mutableListOf())
                }
            }
            cursor.feeds += entry
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>${escape(title)}</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        render(root, depth = 2, out = sb)
        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    private fun render(node: Node, depth: Int, out: StringBuilder) {
        val indent = "  ".repeat(depth)
        for (feed in node.feeds) {
            out.append("$indent<outline type=\"rss\" text=\"${escape(feed.title)}\" xmlUrl=\"${escape(feed.xmlUrl)}\"")
            if (feed.htmlUrl != null) out.append(" htmlUrl=\"${escape(feed.htmlUrl)}\"")
            out.append("/>\n")
        }
        // LinkedHashMap 保持插入序（= 导入时的分组顺序）
        for (child in node.children.values) {
            out.append("$indent<outline text=\"${escape(child.name)}\">\n")
            render(child, depth + 1, out)
            out.append("$indent</outline>\n")
        }
    }

    /** XML 属性转义：不转义会在标题里带 & 或引号时导出坏文档。 */
    private fun escape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private class Node(
        val name: String,
        val children: MutableMap<String, Node>,
        val feeds: MutableList<OpmlEntry>,
    )
}
