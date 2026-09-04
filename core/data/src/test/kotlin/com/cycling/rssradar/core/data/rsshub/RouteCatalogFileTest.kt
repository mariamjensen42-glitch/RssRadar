package com.cycling.rssradar.core.data.rsshub

import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.model.rsshub.RouteCatalog

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * slim schema 的解析（ADR-0010）。
 *
 * 内置快照（assets）与在线更新后的缓存共用这份格式，也是 python 脚本
 * `scripts/build-route-catalog.py` 的输出契约——这里锁住解析行为。
 */
class RouteCatalogFileTest {

    private val file = """
        {
          "v": 1,
          "generatedAt": 1756000000000,
          "namespaces": {
            "bilibili": {
              "n": "哔哩哔哩",
              "u": "www.bilibili.com",
              "c": ["social-media"],
              "h": 221157,
              "r": [
                {
                  "p": "/bilibili/user/video/:uid/:embed?",
                  "n": "UP 主投稿",
                  "h": 179600,
                  "c": ["social-media", "popular"],
                  "pm": {"uid": "用户 id", "embed": "默认为开启内嵌视频"},
                  "pd": {"embed": "1"},
                  "e": [{"p": "/bilibili/user/video/2267573", "t": "示例标题"}]
                }
              ]
            }
          }
        }
    """.trimIndent()

    private fun catalog(): RouteCatalog =
        CATALOG_JSON.decodeFromString<RouteCatalogFile>(file).toCatalog(CatalogSource.BUILTIN)

    @Test
    fun `解析出命名空间与路由`() {
        val route = catalog().routes.single()

        assertEquals("哔哩哔哩", route.sourceName)
        assertEquals("www.bilibili.com", route.sourceUrl)
        assertEquals("/bilibili/user/video/:uid/:embed?", route.path)
        assertEquals(179_600L, route.heat)
        assertEquals(listOf("social-media", "popular"), route.categories)
        assertEquals(listOf("/bilibili/user/video/2267573"), route.examples.map { it.path })
        assertEquals("示例标题", route.examples.single().title)
        assertEquals(1_756_000_000_000L, catalog().generatedAtMillis)
        assertEquals(CatalogSource.BUILTIN, catalog().source)
    }

    @Test
    fun `参数按 path 顺序解析，可选与默认值就位`() {
        val params = catalog().routes.single().params

        assertEquals(listOf("uid", "embed"), params.map { it.key })
        assertEquals(false, params[0].optional)
        assertEquals(true, params[1].optional)
        assertEquals("用户 id", params[0].label)
        assertEquals("1", params[1].defaultValue)
        assertEquals("1", params[1].fallback)
        assertEquals(null, params[0].fallback)
    }

    @Test
    fun `参数说明是 markdown 表格时标签回落为参数名`() {
        val table = """
            {"v":1,"generatedAt":0,"namespaces":{
              "x":{"n":"X","r":[{"p":"/x/:a","n":"N","pm":{"a":"| 键 | 含义 | 接受的值 | 默认值 |"}}]}}}
        """.trimIndent()

        val route = CATALOG_JSON.decodeFromString<RouteCatalogFile>(table)
            .toCatalog(CatalogSource.BUILTIN).routes.single()

        assertEquals("a", route.params.single().label)
    }

    @Test
    fun `路由缺失分类时回落到命名空间的分类`() {
        val fallback = """
            {"v":1,"generatedAt":0,"namespaces":{
              "y":{"n":"Y","c":["game"],"r":[{"p":"/y/z","n":"Z"}]}}}
        """.trimIndent()

        val route = CATALOG_JSON.decodeFromString<RouteCatalogFile>(fallback)
            .toCatalog(CatalogSource.BUILTIN).routes.single()

        assertEquals(listOf("game"), route.categories)
    }

    @Test
    fun `路由按热度全局降序`() {
        val two = """
            {"v":1,"generatedAt":0,"namespaces":{
              "a":{"n":"A","r":[{"p":"/a/1","n":"1","h":1}]},
              "b":{"n":"B","r":[{"p":"/b/9","n":"9","h":9}]},
              "c":{"n":"C","r":[{"p":"/c/5","n":"5","h":5}]}}}
        """.trimIndent()

        val catalog = CATALOG_JSON.decodeFromString<RouteCatalogFile>(two).toCatalog(CatalogSource.BUILTIN)

        assertEquals(listOf("9", "5", "1"), catalog.routes.map { it.name })
    }

    @Test
    fun `建议分组按分类映射`() {
        val programming = """
            {"v":1,"generatedAt":0,"namespaces":{
              "gh":{"n":"GitHub","r":[{"p":"/gh/x","n":"X","c":["programming"]}]}}}
        """.trimIndent()

        val route = CATALOG_JSON.decodeFromString<RouteCatalogFile>(programming)
            .toCatalog(CatalogSource.BUILTIN).routes.single()

        assertEquals("开发", route.suggestedGroup)
    }
}
