package com.cycling.rssradar.core.model.rsshub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 目录检索规则（ADR-0010）：排序、分类筛选、结果上限。 */
class RouteCatalogQueryTest {

    private fun route(
        path: String,
        name: String,
        namespace: String = path.trim('/').substringBefore('/'),
        heat: Long = 0,
        categories: List<String> = emptyList(),
        examples: List<RouteExample> = emptyList(),
    ) = RssHubRoute(
        path = path,
        name = name,
        namespace = namespace,
        sourceName = namespace,
        sourceUrl = "",
        categories = categories,
        heat = heat,
        examples = examples,
    )

    private val routes = listOf(
        route("/bilibili/user/dynamic/:uid", "UP 主动态", "bilibili", heat = 22_398),
        route("/bilibili/user/video/:uid/:embed?", "UP 主投稿", "bilibili", heat = 179_600),
        route("/zhihu/hot/:category?", "知乎热榜", "zhihu", heat = 15_691, categories = listOf(RouteCategory.POPULAR)),
    )

    @Test
    fun `空查询按热度降序返回`() {
        val result = RouteCatalogQuery.search(routes, "", RouteCategory.ALL)

        assertEquals(listOf("UP 主投稿", "UP 主动态", "知乎热榜"), result.map { it.name })
    }

    @Test
    fun `分类筛选只留命中的路由`() {
        val result = RouteCatalogQuery.search(routes, "", RouteCategory.POPULAR)

        assertEquals(listOf("知乎热榜"), result.map { it.name })
    }

    @Test
    fun `名字前缀匹配排在包含匹配之前`() {
        val candidates = listOf(
            route("/a/one", "哔哩哔哩 UP 主动态", "bilibili", heat = 1),
            route("/a/two", "UP 主投稿", "bilibili", heat = 999),
        )

        val result = RouteCatalogQuery.search(candidates, "UP", RouteCategory.ALL)

        assertEquals("UP 主投稿", result.first().name)
    }

    @Test
    fun `命名空间与示例标题也参与匹配`() {
        val withExample = listOf(
            route(
                "/bilibili/user/video/:uid",
                "投稿",
                "bilibili",
                examples = listOf(RouteExample("/bilibili/user/video/1", "影视飓风")),
            ),
        )

        assertEquals(1, RouteCatalogQuery.search(withExample, "影视飓风", RouteCategory.ALL).size)
        assertEquals(1, RouteCatalogQuery.search(withExample, "bilibili", RouteCategory.ALL).size)
    }

    @Test
    fun `结果条数不超过上限`() {
        val many = (1..300).map { route("/a/r$it", "a$it", "a") }

        assertEquals(RouteCatalogQuery.RESULT_LIMIT, RouteCatalogQuery.search(many, "a", RouteCategory.ALL).size)
    }

    @Test
    fun `无命中返回空`() {
        assertTrue(RouteCatalogQuery.search(routes, "绝不匹配的关键字", RouteCategory.ALL).isEmpty())
    }

    @Test
    fun `分类与查询同时生效`() {
        assertTrue(RouteCatalogQuery.search(routes, "UP", RouteCategory.POPULAR).isEmpty())
    }
}
