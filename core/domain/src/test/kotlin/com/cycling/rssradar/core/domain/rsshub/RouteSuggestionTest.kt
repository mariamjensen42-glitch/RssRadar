package com.cycling.rssradar.core.domain.rsshub

import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「猜你想订」匹配的守门测试。
 *
 * 这里错的代价是**静默的**：匹配不上不会报错，只是那条建议从"一键填参"退化成"填进地址栏碰运气"；
 * 匹配错了更糟——用户点开一条推荐，进到的却是另一个栏目。
 */
class RouteSuggestionTest {

    private fun route(path: String, heat: Long = 0, name: String = path) =
        RssHubRoute(
            path = path,
            name = name,
            namespace = path.trim('/').substringBefore('/'),
            sourceName = "来源",
            sourceUrl = "https://example.com",
            categories = emptyList(),
            heat = heat,
        )

    private val catalog = listOf(
        route("/bilibili/user/video/:uid/:embed?", heat = 500),
        route("/zhihu/hot", heat = 900),
        route("/zhihu/hot/:category?", heat = 100),
        route("/weibo/user/:uid", heat = 300),
    )

    @Test
    fun `带参数占位符的路径按字面骨架命中`() {
        val hit = RouteSuggestion.resolve("/bilibili/user/video", catalog)
        assertEquals("/bilibili/user/video/:uid/:embed?", hit?.path)
    }

    @Test
    fun `无参路由逐字命中而不是被骨架匹配抢走`() {
        val hit = RouteSuggestion.resolve("/zhihu/hot", catalog)
        assertEquals("/zhihu/hot", hit?.path)
    }

    @Test
    fun `缺首斜杠也能命中`() {
        assertEquals("/zhihu/hot", RouteSuggestion.resolve("zhihu/hot", catalog)?.path)
    }

    @Test
    fun `同骨架时取热度更高的那条`() {
        // 建议带了目录里不存在的参数名 ⇒ 精确匹配失败、退到骨架比较：
        // /zhihu/hot 与 /zhihu/hot/:category? 骨架相同，热度 900 的那条该赢
        assertEquals("/zhihu/hot", RouteSuggestion.resolve("/zhihu/hot/:category", catalog)?.path)
    }

    @Test
    fun `骨架相同时的结果不依赖目录里的顺序`() {
        val flipped = listOf(catalog[2], catalog[1])
        assertEquals("/zhihu/hot", RouteSuggestion.resolve("/zhihu/hot/:category", flipped)?.path)
    }

    @Test
    fun `目录里没有的建议返回 null`() {
        assertNull(RouteSuggestion.resolve("/douyin/user/:uid", catalog))
    }

    @Test
    fun `空建议与空目录都返回 null`() {
        assertNull(RouteSuggestion.resolve("   ", catalog))
        assertNull(RouteSuggestion.resolve("/zhihu/hot", emptyList()))
    }

    @Test
    fun `纯参数路径不会匹配到任何路由`() {
        // 骨架为空 ⇒ 必须直接放弃，否则它会去匹配所有骨架同样为空的路径
        assertNull(RouteSuggestion.resolve("/:uid/:embed", catalog))
    }

    @Test
    fun `带正则约束的参数同样按骨架匹配`() {
        val constrained = listOf(route("/tieba/forum/:kw{[a-z]+}/good"))
        assertEquals("/tieba/forum/:kw{[a-z]+}/good", RouteSuggestion.resolve("/tieba/forum/good", constrained)?.path)
    }

    @Test
    fun `跨段通配参数也只是一段参数`() {
        val spanning = listOf(route("/gov/category/:path{.+}"))
        assertEquals("/gov/category/:path{.+}", RouteSuggestion.resolve("/gov/category", spanning)?.path)
    }
}
