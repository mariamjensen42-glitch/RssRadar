package com.cycling.rssradar.ui.article

import org.jsoup.Jsoup

/**
 * 正文图片的两件纯 JVM 事务（ReadYou 差距表第 19 项）。数据进、字符串出，
 * 不碰 Android 与 Compose，单测直接覆盖。
 *
 * 1. [extract]：按文档顺序列出正文图片地址（去重，只收 http(s)），供全屏查看页
 *    做多图翻页与"点的是第几张"的定位。
 * 2. [wrapForMaximize]：把每张未包在 `<a>` 里的 `<img>` 套一层指向自身的链接，
 *    让 **JS 已禁用** 的 WebView（ADR-0007）也能把"点图"当成"点链接"上报，
 *    由 WebViewClient 按地址是否命中图片集合分流到全屏查看页（ADR-0011）。
 *    本来就是链接的图（`<a href=页面><img></a>`）保持原语义，不抢它的点击。
 *
 * 为什么不用 JS：ADR-0007 的 OOM 结论是"整页包高 WebView 同时解码所有图片"，
 * 与 JS 无关，但开 JS 会把内存与攻击面一起放大；本方案零 JS 达成同样效果。
 */
object ReadingImages {

    /** 包装出来的链接类名，CSS 用 `.img-link` 去掉下划线与链接色。 */
    const val IMG_LINK_CLASS = "img-link"

    /** 按文档顺序提取，过滤掉相对路径与空 src（sanitize 后的正文里不应出现）。 */
    fun extract(html: String): List<String> {
        val out = LinkedHashSet<String>()
        for (el in Jsoup.parseBodyFragment(html).select("img")) {
            val src = el.attr("src").trim()
            if (src.startsWith("http://") || src.startsWith("https://")) out += src
        }
        return out.toList()
    }

    /**
     * 给 [html] 里命中 [imageUrls] 且未被 `<a>` 包裹的 `<img>` 套一层
     * `<a class="img-link" href=图片地址>`。
     *
     * [imageUrls] 来自 [extract]，一般就是本文的全部图片地址；传空集合直接原样返回，
     * 等价于"点击放大关闭"时的行为。
     */
    fun wrapForMaximize(html: String, imageUrls: Set<String>): String {
        if (imageUrls.isEmpty()) return html
        val body = Jsoup.parseBodyFragment(html).body()
        // 先取快照再改：wrap 会插入父节点，边遍历边改树不可靠。
        for (img in body.select("img").toList()) {
            val src = img.attr("src").trim()
            if (src !in imageUrls) continue
            if (img.parent()?.tagName()?.equals("a", ignoreCase = true) == true) continue
            img.wrap("""<a class="$IMG_LINK_CLASS" href="$src"></a>""")
        }
        return body.html()
    }
}
