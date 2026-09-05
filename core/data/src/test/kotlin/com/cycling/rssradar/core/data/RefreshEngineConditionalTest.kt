package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.domain.rss.ConditionalFetchResult
import com.cycling.rssradar.core.domain.rss.ConditionalHttpFetcher
import com.cycling.rssradar.core.domain.rss.FeedFailureCategory
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.reflect.Proxy

/**
 * 增量刷新的源级捷径（HTTP 协商，v15）：304 直接算成功且零写库；
 * 200 后把本轮凭证写回；解析失败不能留下「没更新过的凭证」。
 */
class RefreshEngineConditionalTest {

    private val rssBody = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
        <title>T</title><link>https://example.com</link><description>d</description>
        </channel></rss>
    """.toByteArray()

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> daoProxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as T

    /** feed 带 ETag 凭证，记录 DAO 的每一次写操作（方法名 → 参数）。 */
    private inner class Fixture(
        /**
         * 注意参数顺序：可尾随 lambda 必须是**最后一个**参数，
         * 否则 `Fixture { ... }` 会把 lambda 绑到 withFailures 上编不过。
         */
        private val withFailures: Boolean = false,
        private val conditionalResult: (url: String, etag: String?, lastModified: String?) -> ConditionalFetchResult,
    ) {
        val validators = mutableListOf<Pair<String, Array<Any?>>>()
        val healthWrites = mutableListOf<Pair<String, Array<Any?>>>()
        val feedDao: FeedDao = daoProxy { name, args ->
            when (name) {
                "getById" -> FeedEntity(
                    id = 1L,
                    url = "https://example.com/1.xml",
                    title = "F",
                    createdAt = 0,
                    etag = "etag-1",
                    lastModified = "last-modified-1",
                    consecutiveFailures = if (withFailures) 1 else 0,
                    lastSuccessAt = if (withFailures) null else 0L,
                )
                "updateValidators" -> validators += name to (args ?: emptyArray())
                // #82 失效检测埋点：成功恢复 / 失败计数，单独记账不混入凭证写入
                "recordRefreshSuccess", "recordRefreshFailure" ->
                    healthWrites += name to (args ?: emptyArray())
                else -> throw UnsupportedOperationException(name)
            }
        }
        val articleDao: ArticleDao = daoProxy { name, _ ->
            throw UnsupportedOperationException(name)
        }
        val engine = RefreshEngine(
            feedDao = feedDao,
            articleDao = articleDao,
            parser = com.cycling.rssradar.core.data.parser.RssParser(),
            http = HttpFetcher { throw IOException("无条件路径不应被走到") },
            conditionalHttp = ConditionalHttpFetcher { url, etag, lastModified ->
                assertEquals("凭证要随请求带上", "https://example.com/1.xml", url)
                assertEquals("etag-1", etag)
                assertEquals("last-modified-1", lastModified)
                conditionalResult(url, etag, lastModified)
            },
        )
    }

    @Test
    fun `304 算刷新成功且零写库`() = runBlocking {
        val f = Fixture { _, _, _ -> ConditionalFetchResult.NotModified }
        val ok = f.engine.refreshSingle(1L)
        assertEquals("304 应视为成功（源未变不是失败）", true, ok)
        assertEquals("零写库：不更新凭证也不动文章", 0, f.validators.size)
        assertEquals("健康源（有成功史、无失败计数）连健康埋点都不写", 0, f.healthWrites.size)
    }

    @Test
    fun `200 后写入本轮协商凭证`() = runBlocking {
        val f = Fixture { _, _, _ ->
            ConditionalFetchResult.Modified(
                body = ByteArrayInputStream(rssBody),
                etag = "etag-2",
                lastModified = "last-modified-2",
            )
        }
        val ok = f.engine.refreshSingle(1L)
        assertEquals(true, ok)
        assertEquals(1, f.validators.size)
        val (name, args) = f.validators.single()
        assertEquals("updateValidators", name)
        assertEquals(1L, args[0])
        assertEquals("etag-2", args[1])
        assertEquals("last-modified-2", args[2])
        assertEquals("健康源成功不写健康埋点", 0, f.healthWrites.size)
    }

    @Test
    fun `解析失败不写凭证并记为无效feed失败`() = runBlocking {
        val f = Fixture { _, _, _ ->
            // 200 但 body 是非法 XML：解析抛 IllegalArgumentException
            ConditionalFetchResult.Modified(
                body = ByteArrayInputStream("not xml at all".toByteArray()),
                etag = "etag-bad",
                lastModified = null,
            )
        }
        val ok = f.engine.refreshSingle(1L)
        assertEquals(false, ok)
        assertEquals("失败刷新不得更新凭证", 0, f.validators.size)
        // #82：失败要留痕——非法 feed 属高置信分类，连续 2 次即失效
        val (_, args) = f.healthWrites.single()
        assertEquals("recordRefreshFailure", f.healthWrites.single().first)
        assertEquals(FeedFailureCategory.INVALID_FEED.stored, args[1])
    }

    @Test
    fun `网络失败按失败处理`() = runBlocking {
        val f = Fixture { _, _, _ -> throw IOException("refused") }
        val ok = f.engine.refreshSingle(1L)
        assertEquals(false, ok)
        assertEquals(0, f.validators.size)
        assertEquals("recordRefreshFailure", f.healthWrites.single().first)
        assertEquals(FeedFailureCategory.NETWORK.stored, f.healthWrites.single().second[1])
    }

    @Test
    fun `伤员源刷新成功触发恢复清零`() = runBlocking {
        // 带失败计数的源走 304：内容没变算成功，恢复清零要真实发生（#82 恢复规则）
        val f = Fixture(withFailures = true) { _, _, _ -> ConditionalFetchResult.NotModified }
        val ok = f.engine.refreshSingle(1L)
        assertEquals(true, ok)
        assertEquals(0, f.validators.size)
        val (name, args) = f.healthWrites.single()
        assertEquals("recordRefreshSuccess", name)
        assertEquals(1L, args[0])
    }
}
