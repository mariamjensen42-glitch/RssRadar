package com.cycling.rssradar.core.domain.rsshub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 路由 path 的参数语法（ADR-0010）。
 *
 * 这是目录能用的关键：3800 条路由里有 1546 条含可选参数、168 条带正则约束，
 * 拼错一个斜杠就是 404。语法解析全部走纯函数，必须锁死。
 */
class RoutePathTest {

    @Test
    fun `解析必填与可选参数`() {
        val params = RoutePath.params("/bilibili/user/video/:uid/:embed?")

        assertEquals(listOf("uid", "embed"), params.map { it.key })
        assertEquals(false, params[0].optional)
        assertEquals(true, params[1].optional)
    }

    @Test
    fun `解析嵌套花括号的正则约束`() {
        // 真实数据里的 /discuz/:ver{[7x]}/:cid{[0-9]{2}}/:link{.+}
        val params = RoutePath.params("/discuz/:ver{[7x]}/:cid{[0-9]{2}}/:link{.+}")

        assertEquals(listOf("ver", "cid", "link"), params.map { it.key })
        assertEquals("[7x]", params[0].pattern)
        assertEquals("[0-9]{2}", params[1].pattern)
        assertEquals(".+", params[2].pattern)
        assertEquals(true, params[2].spansSegments)
        assertEquals(false, params[0].spansSegments)
    }

    @Test
    fun `必填参数缺失时拼不出 URL`() {
        assertNull(RoutePath.build("/bilibili/user/video/:uid", emptyMap()))
        assertNull(RoutePath.build("/bilibili/user/video/:uid", mapOf("uid" to "  ")))
    }

    @Test
    fun `可选参数留空时整段省略`() {
        assertEquals("/zhihu/hot", RoutePath.build("/zhihu/hot/:category?", emptyMap()))
        assertEquals("/zhihu/hot/total", RoutePath.build("/zhihu/hot/:category?", mapOf("category" to "total")))
    }

    @Test
    fun `中间可选项留空不会留下空段`() {
        val path = "/bilibili/platform/:area?/:p_type?/:uid?"

        assertEquals("/bilibili/platform/-1", RoutePath.build(path, mapOf("area" to "-1")))
        assertEquals("/bilibili/platform", RoutePath.build(path, emptyMap()))
    }

    @Test
    fun `跨段参数的值不编码斜杠`() {
        assertEquals(
            "/81/81rc/sy/gzdt_210283",
            RoutePath.build("/81/81rc/:category{.+}?", mapOf("category" to "sy/gzdt_210283")),
        )
    }

    @Test
    fun `普通参数值会被 URL 编码`() {
        assertEquals("/sspai/search/a%20b", RoutePath.build("/sspai/search/:keyword", mapOf("keyword" to "a b")))
    }

    @Test
    fun `从示例反填参数`() {
        assertEquals(
            mapOf("uid" to "2267573"),
            RoutePath.match("/bilibili/user/video/:uid/:embed?", "/bilibili/user/video/2267573"),
        )
    }

    @Test
    fun `示例带可选参数时一并反填`() {
        assertEquals(
            mapOf("uid" to "1195230310", "routeParams" to "1"),
            RoutePath.match("/weibo/user/:uid/:routeParams?", "/weibo/user/1195230310/1"),
        )
    }

    @Test
    fun `通配参数吃掉剩余多段`() {
        assertEquals(
            mapOf("category" to "sy/gzdt_210283"),
            RoutePath.match("/81/81rc/:category{.+}?", "/81/81rc/sy/gzdt_210283"),
        )
    }

    @Test
    fun `通配参数在下一段字面量处收住`() {
        assertEquals(
            mapOf("category" to "sy/gzdt", "id" to "12"),
            RoutePath.match("/x/:category{.+}/item/:id", "/x/sy/gzdt/item/12"),
        )
    }

    @Test
    fun `结构对不上时拒绝反填`() {
        assertNull(RoutePath.match("/bilibili/user/video/:uid", "/bilibili/user/dynamic/2267573"))
        assertNull(RoutePath.match("/github/activity/:user", "/github/activity"))
        assertNull(RoutePath.match("/zhihu/daily", "/zhihu/daily/extra"))
    }
}
