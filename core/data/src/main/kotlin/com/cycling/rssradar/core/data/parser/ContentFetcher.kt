package com.cycling.rssradar.core.data.parser

import com.cycling.rssradar.core.domain.rss.hostOf
import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets

/** 一次抓取的可观测结果：诊断页清单与警告日志都出自这里。 */
data class FetchReport(
    val url: String,
    val finalUrl: String,
    val host: String,
    /** 最后一次 HTTP 状态码；缓存命中为 null。 */
    val statusCode: Int?,
    val attempts: Int,
    val durationMs: Long,
    val bytes: Int,
    val contentChars: Int,
    val extractor: Extractor?,
    val issue: ExtractionIssue?,
    /** 失败原因；成功时为 null。 */
    val failure: FetchFailure? = null,
) {
    val isSuccess: Boolean get() = failure == null
}

sealed interface FetchOutcome {
    data class Success(val content: FetchedContent, val report: FetchReport) : FetchOutcome
    data class Failure(val kind: FetchFailure, val report: FetchReport) : FetchOutcome
}

/** 提取出的网页正文（含元数据与完整性判定）。 */
data class FetchedContent(
    /** 已过 [RssParser.sanitizeHtml] 的正文 HTML。 */
    val contentHtml: String,
    val contentText: String,
    val coverUrl: String?,
    val title: String?,
    val author: String?,
    val publishedAt: Long?,
    /** false = 不完整（过短 / 无段落 / JS 空壳 / 付费墙），仍写入但必须打标记。 */
    val isComplete: Boolean,
    val issue: ExtractionIssue,
    val extractor: Extractor,
)

data class FetchConfig(
    val connectTimeoutMs: Int = 8_000,
    val readTimeoutMs: Int = 15_000,
    /** 总尝试次数（含首次）。 */
    val maxAttempts: Int = 3,
    val backoffBaseMs: Long = 600L,
    /** 429 的 Retry-After 上限，超过就直接放弃（站点让我们等太久）。 */
    val maxRetryAfterMs: Long = 8_000L,
    val userAgent: String = DEFAULT_USER_AGENT,
    val extraHeaders: Map<String, String> = DEFAULT_HEADERS,
    /** null = 跟随系统代理；显式指定则走该代理。 */
    val proxy: Proxy? = null,
    val extract: ExtractConfig = ExtractConfig(),
) {
    companion object {
        // 桌面 Chrome：大量站点对移动端 UA 返回简化页或直接 403（旧实现用的是自报家门的 RssRadar UA）。
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        val DEFAULT_HEADERS = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        )
    }
}

/** 日志出口。抽成接口是让 ContentFetcher 保持纯 JVM（Android 的 Log 在单测里是 stub，一调用就抛）。 */
interface FetchLogger {
    enum class Level { INFO, WARN, ERROR }

    fun log(level: Level, message: String, throwable: Throwable? = null)

    object NoOp : FetchLogger {
        override fun log(level: Level, message: String, throwable: Throwable?) = Unit
    }
}

/**
 * 按需抓取原网页并提取正文。
 *
 * 相比旧实现补了三件事：
 * 1. **重试与退避**：只有超时/网络/429/5xx 重试（401/403/404 重试无意义），429 尊重 Retry-After。
 * 2. **状态码可见**：每次尝试的 status / attempts / 耗时 / 字节数都进 [FetchReport]，失败有 [FetchFailure] 分类。
 * 3. **缓存原始 HTML**：旧实现缓存的是提取结果，提取算法升级后老缓存永远不生效；现在缓存原始响应，命中后重跑提取。
 *
 * 完整性由 [Readability] 判定：过短 / 无段落 / JS 空壳 / 付费墙都会 `isComplete = false` 并输出 WARN——
 * **但仍然写入**（比空白页好），由上层打「不完整」标记，不再静默。
 */
class ContentFetcher(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val config: FetchConfig = FetchConfig(),
    private val logger: FetchLogger = FetchLogger.NoOp,
) {

    suspend fun fetch(link: String): FetchOutcome = withContext(ioDispatcher) {
        val started = System.currentTimeMillis()
        val host = hostOf(link)

        // 命中缓存后照旧走提取：缓存存的是原始响应，提取算法升级后老缓存依然生效。
        // 先剥掉格式标记，别让它混进 rawHtml（会算进 FetchReport.bytes）。
        val cached = runCatching {
            cacheFileFor(link).takeIf { it.exists() }?.readText()?.removePrefix(RAW_CACHE_MARKER)
        }.getOrNull()
        if (cached != null) {
            return@withContext extractOutcome(
                link = link,
                rawHtml = cached,
                statusCode = null,
                attempts = 0,
                started = started,
            )
        }

        var attempts = 0
        var lastStatus: Int? = null
        var lastKind = FetchFailure.NETWORK
        while (attempts < config.maxAttempts) {
            attempts++
            when (val result = download(link)) {
                is Download.Ok -> {
                    cacheRaw(link, result.html)
                    // 提取成功但内容不完整时不再重试：换 UA 也拿不到被 JS/付费墙挡住的内容
                    return@withContext extractOutcome(link, result.html, result.status, attempts, started)
                }
                is Download.Err -> {
                    lastStatus = result.status
                    lastKind = result.kind
                    logger.log(
                        FetchLogger.Level.WARN,
                        "抓取失败 attempt=$attempts/${config.maxAttempts} kind=${result.kind} " +
                            "status=${result.status} host=$host url=$link",
                    )
                    if (!result.kind.retryable || attempts >= config.maxAttempts) break
                    delay(result.retryAfterMs ?: (config.backoffBaseMs * (1L shl (attempts - 1))))
                }
            }
        }

        logger.log(
            FetchLogger.Level.WARN,
            "抓取放弃 kind=$lastKind status=$lastStatus attempts=$attempts host=$host url=$link",
        )
        FetchOutcome.Failure(
            lastKind,
            FetchReport(
                url = link,
                finalUrl = link,
                host = host,
                statusCode = lastStatus,
                attempts = attempts,
                durationMs = elapsed(started),
                bytes = 0,
                contentChars = 0,
                extractor = null,
                issue = null,
                failure = lastKind,
            ),
        )
    }

    // ———————————————————————————————————————————————
    // 提取
    // ———————————————————————————————————————————————

    private fun extractOutcome(
        link: String,
        rawHtml: String,
        statusCode: Int?,
        attempts: Int,
        started: Long,
    ): FetchOutcome {
        val host = hostOf(link)
        val first = Readability.extract(link, rawHtml, config.extract)
        if (first == null) {
            logger.log(FetchLogger.Level.WARN, "正文提取失败 host=$host url=$link")
            return FetchOutcome.Failure(
                FetchFailure.EXTRACT_FAILED,
                FetchReport(
                    url = link,
                    finalUrl = link,
                    host = host,
                    statusCode = statusCode,
                    attempts = attempts,
                    durationMs = elapsed(started),
                    bytes = rawHtml.length,
                    contentChars = 0,
                    extractor = null,
                    issue = null,
                    failure = FetchFailure.EXTRACT_FAILED,
                ),
            )
        }

        val stats = measure(first.contentHtml)
        val content = FetchedContent(
            contentHtml = first.contentHtml,
            contentText = first.contentText,
            coverUrl = first.coverUrl,
            title = first.title,
            author = first.author,
            publishedAt = first.publishedAt,
            isComplete = first.quality.isComplete,
            issue = first.quality.issue,
            extractor = first.quality.extractor,
        )
        val report = FetchReport(
            url = link,
            finalUrl = link,
            host = host,
            statusCode = statusCode,
            attempts = attempts,
            durationMs = elapsed(started),
            bytes = rawHtml.length,
            contentChars = stats.chars,
            extractor = content.extractor,
            issue = content.issue,
        )
        if (content.isComplete) {
            logger.log(
                FetchLogger.Level.INFO,
                "抓取成功 chars=${stats.chars} extractor=${content.extractor} host=$host",
            )
        } else {
            logger.log(
                FetchLogger.Level.WARN,
                "正文不完整 issue=${content.issue} chars=${stats.chars} paragraphs=${stats.paragraphs} " +
                    "extractor=${content.extractor} host=$host url=$link",
            )
        }
        return FetchOutcome.Success(content, report)
    }

    private fun measure(html: String): ContentStats {
        val doc = Jsoup.parseBodyFragment(html)
        return ContentStats(doc.text().length, doc.select("p").size)
    }

    private data class ContentStats(val chars: Int, val paragraphs: Int)

    // ———————————————————————————————————————————————
    // 下载
    // ———————————————————————————————————————————————

    private sealed interface Download {
        data class Ok(val html: String, val status: Int) : Download
        data class Err(val kind: FetchFailure, val status: Int?, val retryAfterMs: Long?) : Download
    }

    private fun download(link: String): Download {
        val connection = try {
            val url = URL(link)
            (if (config.proxy != null) url.openConnection(config.proxy) else url.openConnection()) as HttpURLConnection
        } catch (_: Exception) {
            return Download.Err(FetchFailure.INVALID_URL, null, null)
        }
        return try {
            connection.connectTimeout = config.connectTimeoutMs
            connection.readTimeout = config.readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", config.userAgent)
            config.extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            runCatching { java.net.URI(link) }.getOrNull()?.let { uri ->
                if (uri.scheme != null && uri.host != null) {
                    connection.setRequestProperty("Referer", "${uri.scheme}://${uri.host}/")
                }
            }

            when (val code = connection.responseCode) {
                in 200..299 -> {
                    // 链接本身就是资源（图片/PDF 等）时直接放弃：不校验的话，JPEG 二进制
                    // 会被当文本解码成乱码写进正文 —— 「link 即图片」的源（必应每日壁纸）
                    // 就靠这条挡住。服务器不给 Content-Type 时不拦，不能因此放掉正常抓取。
                    val contentType = connection.contentType
                    if (contentType != null && !contentType.isWebPageLike()) {
                        return Download.Err(FetchFailure.NOT_HTML, code, null)
                    }
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return Download.Err(FetchFailure.EMPTY_BODY, code, null)
                    val html = decode(bytes, connection.contentType)
                        ?: return Download.Err(FetchFailure.DECODE_ERROR, code, null)
                    Download.Ok(html, code)
                }
                401 -> Download.Err(FetchFailure.HTTP_401, code, null)
                403 -> Download.Err(FetchFailure.HTTP_403, code, null)
                404 -> Download.Err(FetchFailure.HTTP_404, code, null)
                429 -> Download.Err(FetchFailure.HTTP_429, code, retryAfter(connection))
                in 500..599 -> Download.Err(FetchFailure.HTTP_5XX, code, null)
                else -> Download.Err(FetchFailure.HTTP_OTHER, code, null)
            }
        } catch (_: SocketTimeoutException) {
            Download.Err(FetchFailure.TIMEOUT, null, null)
        } catch (_: IOException) {
            Download.Err(FetchFailure.NETWORK, null, null)
        } catch (_: Exception) {
            Download.Err(FetchFailure.NETWORK, null, null)
        } finally {
            // 错误分支（401/403/404/429/5xx）从不读 errorStream——必须显式关掉，
            // 否则平台 gzip Inflater 等 GC 才 end（真机 CloseGuard 实证）。
            runCatching { connection.errorStream?.close() }
            connection.disconnect()
        }
    }

    /**
     * 响应类型是否可能承载网页正文。
     *
     * 判据取「宽进」：`text/` 开头的一律放行（不少站点用 text/plain 吐正文），含 html/xml 也放行；
     * 只有明确是图片/音视频/PDF/二进制流的才拒。宁可让一份非 HTML 走正常的「提取失败」，
     * 也不要把图片字节当文本塞进库里 —— 后者不会报错，只会在阅读页显示一屏乱码。
     */
    private fun String.isWebPageLike(): Boolean {
        val type = substringBefore(';').trim().lowercase()
        return type.startsWith("text/") || type.contains("html") || type.contains("xml")
    }

    private fun retryAfter(connection: HttpURLConnection): Long? {
        val seconds = connection.getHeaderFieldLong("Retry-After", -1L)
        if (seconds <= 0) return null
        val ms = TimeUnit.SECONDS.toMillis(seconds)
        return ms.takeIf { it <= config.maxRetryAfterMs }
    }

    /**
     * 编码探测：Content-Type 头 → HTML meta charset → UTF-8。
     * 国内站点 GBK/GB2312 实测存在（ReadYou 同样处理）。
     */
    private fun decode(bytes: ByteArray, contentType: String?): String? {
        val headerCharset = contentType?.let {
            Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        val charsetName = headerCharset
            ?: bytes.decodeToString(0, minOf(bytes.size, 2048)).let { head ->
                Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            }
        return runCatching { bytes.toString(charset(charsetName ?: "UTF-8")) }
            .getOrElse { runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() }
    }

    // ———————————————————————————————————————————————
    // 缓存
    // ———————————————————————————————————————————————

    /** 缓存**原始响应**而非提取结果：提取算法升级后老缓存照样能重跑。 */
    private fun cacheRaw(link: String, html: String) {
        runCatching {
            val file = cacheFileFor(link)
            file.parentFile?.mkdirs()
            file.writeText(RAW_CACHE_MARKER + html)
        }
    }

    private fun cacheFileFor(link: String): File =
        File(File(cacheDir, CACHE_DIR_NAME), link.sha256() + ".html")

    private fun elapsed(started: Long): Long = System.currentTimeMillis() - started

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CACHE_DIR_NAME = "content"
        const val RAW_CACHE_MARKER = "<!--rssradar-raw-v1-->"
    }
}
