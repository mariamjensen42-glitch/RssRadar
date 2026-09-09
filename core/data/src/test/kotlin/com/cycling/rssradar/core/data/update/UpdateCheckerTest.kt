package com.cycling.rssradar.core.data.update

import com.cycling.rssradar.core.domain.rss.HttpFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/** 检查更新的判据与三种结果（ReadYou 差距表 #35）。 */
class UpdateCheckerTest {

    private fun checker(body: String) = UpdateChecker(HttpFetcher { ByteArrayInputStream(body.toByteArray()) })

    @Test
    fun `newer version is detected and older one is not`() {
        assertTrue(isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(isNewerVersion("1.0", "1.1"))
        assertFalse("同版本不算更新", isNewerVersion("1.0.1", "1.0.1"))
        assertFalse("本地更新不算更新", isNewerVersion("1.1", "1.0"))
    }

    @Test
    fun `v prefix is tolerated`() {
        assertTrue(isNewerVersion("v1.0.0", "v1.0.1"))
        assertTrue(isNewerVersion("1.0.0", "v1.1.0"))
    }

    @Test
    fun `unparsable versions report no update instead of guessing`() {
        // 比不出来就说没更新——骗用户去升级比漏报一次更糟
        assertFalse(isNewerVersion("1.0.0", "1.0-beta"))
        assertFalse(isNewerVersion("1.0.0", "unknown"))
        assertFalse("位数不齐不猜", isNewerVersion("1.0.1", "1.0"))
        assertFalse(isNewerVersion("", "1.0"))
    }

    @Test
    fun `release json is parsed and garbage is rejected`() {
        val parsed = parseLatestRelease(
            """{"tag_name":"v1.2.0","html_url":"https://github.com/x/y/releases/tag/v1.2.0","name":"1.2.0"}""",
        )
        assertEquals("v1.2.0", parsed?.version)
        assertEquals("1.2.0", parsed?.title)

        assertNull("缺 tag 不该算解析成功", parseLatestRelease("""{"html_url":"x"}"""))
        assertNull(parseLatestRelease("不是 JSON"))
    }

    @Test
    fun `check reports available up to date and failure`() = runBlocking {
        val available = checker(
            """{"tag_name":"v9.9.9","html_url":"https://example.com/r","name":"big"}""",
        ).check("1.0.0")
        assertEquals("v9.9.9", (available as UpdateCheckResult.Available).release.version)

        val upToDate = checker("""{"tag_name":"v1.0.0","html_url":"https://example.com/r"}""").check("1.0.0")
        assertTrue(upToDate is UpdateCheckResult.UpToDate)

        val failed = UpdateChecker(HttpFetcher { throw IOException("no net") }).check("1.0.0")
        assertTrue(failed is UpdateCheckResult.Failed)
        assertTrue((failed as UpdateCheckResult.Failed).message.contains("网络"))
    }
}
