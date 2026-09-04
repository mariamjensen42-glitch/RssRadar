package com.cycling.rssradar.core.data.rsshub

import com.cycling.rssradar.core.data.FakeSharedPreferences
import com.cycling.rssradar.core.domain.rsshub.InstanceProber
import com.cycling.rssradar.core.domain.rsshub.RssHubRoutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「选最快可达实例」策略（真值：探测已抽进 [InstanceProber] 缝，此处塞 fake
 * 不联网即可测——此前这条策略住在 store 里与 HttpURLConnection 同室，永远测不到）。
 *
 * 注意候选池 = 自定义实例 + [RssHubInstanceStore.BUILTIN_INSTANCES]，
 * fake 只对出现在候选池里的 host 给延迟。
 */
class RssHubInstanceStoreTest {

    private val official = "https://rsshub.app"

    private fun store(prober: InstanceProber) =
        RssHubInstanceStore(FakeSharedPreferences(), prober)

    @Test
    fun `并发探测选最快可达者，慢的可达者输给快的`() = runBlocking {
        val custom = "https://custom.example"
        // 自定义可达但慢（2.4s），官方镜像快（0.2s）——选快不报名次。
        val s = store(InstanceProber { host ->
            when (host) {
                custom -> 2_400L
                official -> 200L
                else -> null
            }
        })
        s.customHost = custom
        assertEquals(official, s.detectFirstAvailable())
    }

    @Test
    fun `全部不可达返回 null，不猜`() = runBlocking {
        val s = store(InstanceProber { null })
        s.customHost = "https://dead.example"
        assertEquals(null, s.detectFirstAvailable())
    }

    @Test
    fun `isReachable 按探测结果判定`() = runBlocking {
        val s = store(InstanceProber { host -> if (host == official) 100L else null })
        assertTrue(s.isReachable(official))
        assertFalse(s.isReachable("https://dead.example"))
    }

    @Test
    fun `currentOrDefault 优先级：自定义大于探测记忆大于默认`() {
        val s = store(InstanceProber { null })
        assertEquals(RssHubRoutes.DEFAULT_HOST, s.currentOrDefault())
        s.customHost = "https://custom.example"
        assertEquals("https://custom.example", s.currentOrDefault())
    }
}
