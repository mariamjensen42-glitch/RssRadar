package com.cycling.rssradar.core.domain.concurrency

import kotlin.coroutines.cancellation.CancellationException

/**
 * 与标准库 [runCatching] 同义，但**不吞协程取消**。
 *
 * [runCatching] 捕获 `Throwable`，其中包含 [CancellationException]。用它包住可取消的挂起调用
 * （网络请求、Room 查询）时，取消会被降级成一次普通失败：调用方看到「这次操作失败了」，
 * 于是继续走失败分支——重试、发起下一个请求、把错误态写进 UI。页面早已退出，工作却还在继续，
 * 结构化并发就此断链。取消不是失败，必须原样上抛。
 *
 * 只用于包住挂起调用的位置；同步、不可能被取消的调用点用标准库版本即可。
 */
inline fun <T> quietCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
