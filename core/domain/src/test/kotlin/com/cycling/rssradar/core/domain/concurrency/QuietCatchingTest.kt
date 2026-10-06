package com.cycling.rssradar.core.domain.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 钉住 [quietCatching] 与标准库 [runCatching] 的唯一差别：取消是否穿透。
 *
 * 这条差别不能靠读代码确认——两个函数的成功/失败路径完全一样，只有「协程被取消」
 * 这一条分支不同，而它恰恰是结构化并发的命门，所以必须用差分测试锁定。
 */
class QuietCatchingTest {

    @Test
    fun `成功时返回 Result_success`() {
        val result = quietCatching { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `普通异常回落为 Result_failure`() {
        val result = quietCatching { error("boom") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `取消异常原样上抛而不是变成失败`() {
        try {
            quietCatching { throw CancellationException("cancelled") }
            fail("CancellationException 不应被吞成 Result.failure")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `挂起调用被取消后不再继续执行后续代码`() = runBlocking {
        var continued = false
        val job = async {
            quietCatching { delay(5_000) }.getOrNull()
            continued = true
        }
        delay(50)
        job.cancelAndJoin()
        assertFalse("取消后仍走完了失败分支，等于取消了还继续干活", continued)
    }
}
