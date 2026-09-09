package com.cycling.rssradar.core.data

import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.data.parser.RssParser
import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.Collections

/**
 * 失效源自愈（连续解析失败后 autodiscovery 换地址）的行为契约。
 *
 * 自愈是「改用户数据」的动作（改写 feeds.url），必须有测试钉住三条底线：
 * 命中才改、目标已订阅就不改、改不动就保持原样。
 */
class RefreshEngineHealTest {

    private val oldUrl = "https://example.com/old"
    private val healedUrl = "https://example.com/feed.xml"

    /** 旧地址已经变成普通网页，但页面里声明了真正的 feed。 */
    private val html = """
        <html><head>
        <link rel="alternate" type="application/rss+xml" href="/feed.xml">
        </head><body>this page moved</body></html>
    """.trimIndent()

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>T</title>
        <item><title>A1</title><link>https://example.com/1</link></item>
        </channel></rss>
    """.trimIndent()

    /** 已连续失败 1 次的源：本次是第 2 次，刚好够自愈门槛。 */
    private fun staleFeed(id: Long) = FeedEntity(
        id = id,
        url = oldUrl,
        title = "F",
        createdAt = 0,
        consecutiveFailures = 1,
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> daoProxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
        ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as T

    private inner class Fixture(
        /** 每个候选地址的归属：null = 没人订阅，非 null = 已存在的源 id。 */
        private val subscribed: (String) -> Long? = { null },
        /** 候选地址能否真的解析出 feed。 */
        private val candidateBody: (String) -> String = { rss },
        private val feedCount: Int = 1,
    ) {
        // 必须是同步容器：refreshAll 走 Semaphore(32) 并发，下面的回调会被多路协程同时
        // 调用。普通 ArrayList 并发 add 会丢元素、甚至抛 ArrayIndexOutOfBounds——
        // 而生产代码把 updateUrl 的异常当成「撞唯一索引」吞掉换下一个候选，
        // 于是测试表现成「自愈数量随机少几个」这种隔三差五红一次的假故障。
        val urlUpdates = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())
        val heals = Collections.synchronizedList(mutableListOf<Triple<Long, String, String>>())

        val feedDao: FeedDao = daoProxy { name, args ->
            when (name) {
                "getById" -> staleFeed(args.first() as Long)
                "getAll" -> (1L..feedCount).map(::staleFeed)
                "findIdByUrl" -> subscribed(args.first() as String)
                "updateUrl" -> urlUpdates += (args[0] as Long) to (args[1] as String)
                else -> null
            }
        }

        val articleDao: ArticleDao = daoProxy { name, _ ->
            when (name) {
                "getIdLinkPairsByFeed", "getTombstonedLinks", "insertAll" -> emptyList<Any>()
                else -> null
            }
        }

        val engine = RefreshEngine(
            feedDao = feedDao,
            articleDao = articleDao,
            parser = RssParser(),
            http = HttpFetcher { url ->
                val body = when {
                    url == oldUrl -> html
                    url.startsWith("https://example.com/") -> candidateBody(url)
                    else -> throw IOException("unexpected $url")
                }
                ByteArrayInputStream(body.toByteArray())
            },
            onHealed = { feedId, from, to -> heals += Triple(feedId, from, to) },
        )
    }

    @Test
    fun `自愈命中 改写地址并补刷新成功`() = runBlocking {
        val f = Fixture()

        assertTrue("改地址后补刷新成功才算自愈成功", f.engine.refreshSingle(1L))
        assertEquals(listOf(1L to healedUrl), f.urlUpdates)
        assertEquals(listOf(Triple(1L, oldUrl, healedUrl)), f.heals)
    }

    @Test
    fun `候选已被订阅 不制造重复源也不改地址`() = runBlocking {
        val f = Fixture(subscribed = { 2L })

        assertTrue("自愈失败就该返回 false", !f.engine.refreshSingle(1L))
        assertTrue("不能改写地址", f.urlUpdates.isEmpty())
    }

    @Test
    fun `所有候选都解析不出feed 保持原状`() = runBlocking {
        val f = Fixture(candidateBody = { throw IOException("404") })

        assertTrue(!f.engine.refreshSingle(1L))
        assertTrue("没有可信新地址就不许动 url", f.urlUpdates.isEmpty())
        assertTrue(f.heals.isEmpty())
    }

    @Test
    fun `每轮刷新有自愈配额 不放任坏源拖垮全量刷新`() = runBlocking {
        // 15 个坏源，配额 10：单源最多 10 个请求且全程占并发名额，没有配额会拖垮全量刷新
        val f = Fixture(feedCount = 15)

        val ok = f.engine.refreshAll()

        assertEquals("超配额的源维持失败现状", RefreshEngine.MAX_HEALS_PER_ROUND, ok)
        assertEquals(RefreshEngine.MAX_HEALS_PER_ROUND, f.urlUpdates.size)
    }
}
