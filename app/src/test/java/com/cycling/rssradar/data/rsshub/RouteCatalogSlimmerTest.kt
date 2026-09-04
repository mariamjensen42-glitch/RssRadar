package com.cycling.rssradar.data.rsshub

import com.cycling.rssradar.core.model.rsshub.CatalogSource

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 在线更新链路的精简器（ADR-0010）。
 *
 * 内置快照由 python 脚本按同样规则生成，这里锁住 Kotlin 侧的行为——
 * 两边规则一旦漂移，用户更新目录后会得到与内置快照结构不一致的数据。
 */
class RouteCatalogSlimmerTest {

    private val raw = """
        {
          "weibo": {
            "name": "微博",
            "url": "weibo.com",
            "categories": ["social-media"],
            "heat": 60094,
            "routes": {
              "/weibo/user/:uid/:routeParams?": {
                "name": "博主",
                "heat": 51989,
                "categories": ["social-media"],
                "maintainers": ["someone"],
                "location": "weibo.ts",
                "description": "::: warning 部分博主仅登录可见 :::",
                "example": "/weibo/user/1195230310",
                "parameters": {
                  "uid": "用户 id",
                  "routeParams": {
                    "description": "额外参数",
                    "default": 1,
                    "options": [{"value": "1", "label": "显示视频"}, {"value": "0", "label": "不显示"}]
                  }
                },
                "topFeeds": ["weibo", "/weibo/user/:uid", [
                  {"url": "rsshub://weibo/user/1", "title": "报错的示例", "errorMessage": "500 Internal Server Error"},
                  {"url": "rsshub://weibo/user/2", "title": "健康的示例"}
                ]]
              }
            }
          }
        }
    """.trimIndent()

    private fun slim(): RouteNamespaceEntry =
        RouteCatalogSlimmer.slim(REMOTE_JSON.decodeFromString(raw), 0).namespaces.getValue("weibo")

    @Test
    fun `丢掉无关字段并压平 markdown 提示块`() {
        val route = slim().r.single()

        assertEquals("微博", slim().n)
        assertEquals("weibo.com", slim().u)
        assertEquals("/weibo/user/:uid/:routeParams?", route.p)
        assertEquals("部分博主仅登录可见", route.d)
        assertEquals("用户 id", route.pm["uid"])
    }

    @Test
    fun `参数默认值与可选值就位`() {
        val route = slim().r.single()

        assertEquals("额外参数", route.pm["routeParams"])
        assertEquals("1", route.pd["routeParams"])
        assertEquals(
            listOf("1" to "显示视频", "0" to "不显示"),
            route.po["routeParams"]?.map { it.v to it.l },
        )
    }

    @Test
    fun `健康示例排在报错示例之前`() {
        val examples = slim().r.single().e

        assertEquals(listOf("/weibo/user/2", "/weibo/user/1"), examples.map { it.p })
        assertEquals("健康的示例", examples.first().t)
    }

    @Test
    fun `没有 topFeeds 时退回官方 example`() {
        val fallback = """
            {"z": {"name": "Z", "routes": {
              "/z/a/:id": {"name": "A", "example": "/z/a/42"}
            }}}
        """.trimIndent()

        val route = RouteCatalogSlimmer.slim(REMOTE_JSON.decodeFromString(fallback), 0)
            .namespaces.getValue("z").r.single()

        assertEquals(listOf("/z/a/42"), route.e.map { it.p })
        assertEquals("", route.e.single().t)
    }

    @Test
    fun `path 之外的参数不入表单`() {
        val mismatched = """
            {"m": {"name": "M", "routes": {
              "/m/a/:id": {"name": "A", "parameters": {"id": "编号", "ghost": "文档里多写的参数"}}
            }}}
        """.trimIndent()

        val file = RouteCatalogSlimmer.slim(REMOTE_JSON.decodeFromString(mismatched), 0)
        val route = file.toCatalog(CatalogSource.BUILTIN).routes.single()

        assertEquals(listOf("id"), route.params.map { it.key })
        assertEquals("编号", route.params.single().description)
    }
}
