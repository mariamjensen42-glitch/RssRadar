package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy

/**
 * 回归测试（真机反馈缺口，2026-09-05）：708 源全量刷新可达数十分钟，
 * 只有孤零零的转圈分不清「在跑」还是「卡死」。refreshAll 的进度回调必须：
 * 每完成一个源回调一次、done 单调递增、终值 (total, total)——UI 的「正在刷新 n/N」靠它。
 */
class RefreshEngineProgressTest {

    private val feedCount = 3

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> daoProxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as T

    private fun feedDao(): FeedDao = daoProxy { name, _ ->
        when (name) {
            "getAll" -> (1L..feedCount).map { id ->
                FeedEntity(id = id, url = "https://example.com/$id.xml", title = "F$id", createdAt = 0)
            }
            "getById" -> FeedEntity(id = 1L, url = "https://example.com/1.xml", title = "F", createdAt = 0)
            else -> throw UnsupportedOperationException(name)
        }
    }

    private fun articleDao(): ArticleDao = daoProxy { name, _ ->
        when (name) {
            // 空表：upsert 直接跳过（articles 为空时提前返回，不进事务）
            else -> throw UnsupportedOperationException(name)
        }
    }

    /** http 返回无 <item> 的合法 RSS：刷新走完 upsert 空列表短路，成功返回 true。 */
    private fun engine(): RefreshEngine = RefreshEngine(
        feedDao = feedDao(),
        articleDao = articleDao(),
        parser = com.cycling.rssradar.core.data.parser.RssParser(),
        http = HttpFetcher {
            ByteArrayInputStream(
                """<?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                <title>T</title><link>https://example.com</link><description>d</description>
                </channel></rss>""".toByteArray(),
            )
        },
    )

    @Test
    fun `进度回调逐源递增且终值为总数`() = runBlocking {
        val seen = mutableListOf<Pair<Int, Int>>()
        val synced = java.util.Collections.synchronizedList(seen)
        val success = engine().refreshAll { done, total -> synced += done to total }
        assertEquals("全部源应刷新成功", feedCount, success)
        assertEquals("每个源恰好回调一次", feedCount, seen.size)
        // done 单调递增：8 路并发下回调顺序不定，但值必须严格 1,2,3
        val dones = seen.map { it.first }
        assertEquals((1..feedCount).toList(), dones.sorted())
        seen.forEach { (_, total) -> assertEquals("total 恒为源总数", feedCount, total) }
        // 并发回调序不定，last() 未必是终值；done 恰好为 1..n 各一次（上面 sorted 断言）即保证推进到总数
        assertEquals(feedCount, seen.maxOf { it.first })
    }

    @Test
    fun `失败源也计入进度不让计数卡死`() = runBlocking {
        // URL 指向不可达端口 → 该源失败，但 finally 里进度照走
        val failingDao = daoProxy { name, _ ->
            when (name) {
                "getAll" -> (1L..feedCount).map { id ->
                    FeedEntity(id = id, url = "https://example.com/$id.xml", title = "F$id", createdAt = 0)
                }
                "getById" -> FeedEntity(id = 1L, url = "https://127.0.0.1:1/broken.xml", title = "F", createdAt = 0)
                else -> throw UnsupportedOperationException(name)
            }
        } as FeedDao
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val success = RefreshEngine(
            feedDao = failingDao,
            articleDao = articleDao(),
            parser = com.cycling.rssradar.core.data.parser.RssParser(),
            http = HttpFetcher { throw java.io.IOException("refused") },
        ).refreshAll { done, _ -> seen += done }
        assertEquals("全部源失败，成功数为 0", 0, success)
        assertEquals("失败源同样要推进进度（finally 保证）", feedCount, seen.size)
    }
}
