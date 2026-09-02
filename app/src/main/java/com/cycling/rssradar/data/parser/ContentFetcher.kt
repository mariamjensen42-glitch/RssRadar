package com.cycling.rssradar.data.parser

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

/** 抓取/提取失败的原因分类（诊断页按此归类）。 */
enum class FetchFailure {
    INVALID_URL,
    TIMEOUT,
    NETWORK,
    HTTP_401,
    HTTP_403,
    HTTP_404,
    HTTP_429,
    HTTP_5XX,
    HTTP_OTHER,
    EMPTY_BODY,
    DECODE_ERROR,
    EXTRACT_FAILED,
    ;

    /** 是否值得重试：401/403/404 重试无意义，只会浪费配额并招致更狠的封禁。 */
    val retryable: Boolean
        get() = this == TIMEOUT || this == NETWORK || this == HTTP_429 || this == HTTP_5XX

    val label: String
        get() = when (this) {
            INVALID_URL -> "链接无效"
            TIMEOUT -> "连接/读取超时"
            NETWORK -> "网络不可达"
            HTTP_401 -> "401 需登录"
            HTTP_403 -> "403 拒绝（反爬）"
            HTTP_404 -> "404 页面不存在"
            HTTP_429 -> "429 限流"
            HTTP_5XX -> "服务端 5xx"
            HTTP_OTHER -> "HTTP 其他状态码"
            EMPTY_BODY -> "响应为空"
            DECODE_ERROR -> "编码解码失败"
            EXTRACT_FAILED -> "正文提取失败"
        }
}

/** 一次抓取的可观测结果：诊断页清单与警告日志都出自这里。 */
data class FetchReport(
    val url: String,
    val finalUrl: String,
    val host: String,
    /** 最后一次 HTTP 状态码；缓存命中为 null。 */
    val statusCode: Int?,
    val attempts: Int,
    /** 实际拼接的页数。 */
    val pages: Int,
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
    /** 实际拼接的页数（>1 说明是分页文章）。 */
    val pages: Int,
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
    /** 分页最多拼几页。 */
    val maxPages: Int = 3,
    val userAgents: List<String> = DEFAULT_USER_AGENTS,
    val extraHeaders: Map<String, String> = DEFAULT_HEADERS,
    /** null = 跟随系统代理；显式指定则走该代理。 */
    val proxy: Proxy? = null,
    val extract: ExtractConfig = ExtractConfig(),
) {
    companion object {
        // 桌面 Chrome 优先：大量站点对移动端 UA 返回简化页或直接 403（旧实现用的是自报家门的 RssRadar UA）。
        val DEFAULT_USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        )
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
 * 按需抓取原网页并提取正文（ADR-0001 + ADR-0012）。
 *
 * 相比旧实现补了四件事：
 * 1. **重试与退避**：只有超时/网络/429/5xx 重试（401/403/404 重试无意义），429 尊重 Retry-After。
 * 2. **状态码可见**：每次尝试的 status / attempts / 耗时 / 字节数都进 [FetchReport]，失败有 [FetchFailure] 分类。
 * 3. **分页拼接**：`rel=next` → 「下一页」锚点 → page 参数 +1，最多 [FetchConfig.maxPages] 页，按段落去重。
 * 4. **缓存原始 HTML**：旧实现缓存的是提取结果，提取算法升级后老缓存永远不生效；现在缓存原始响应，命中后重跑提取。
 *
 * 完整性由 [ArticleExtractor] 判定：过短 / 无段落 / JS 空壳 / 付费墙都会 `isComplete = false` 并输出 WARN——
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

        val cached = runCatching { cacheFileFor(link).takeIf { it.exists() }?.readText() }.getOrNull()
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
            when (val result = download(link, attempts)) {
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
            FetchReport(link, link, host, lastStatus, attempts, 0, elapsed(started), 0, 0, null, null, lastKind),
        )
    }

    // ———————————————————————————————————————————————
    // 提取 + 分页拼接
    // ———————————————————————————————————————————————

    private fun extractOutcome(
        link: String,
        rawHtml: String,
        statusCode: Int?,
        attempts: Int,
        started: Long,
    ): FetchOutcome {
        val host = hostOf(link)
        val first = ArticleExtractor.extract(link, rawHtml, config.extract)
        if (first == null) {
            logger.log(FetchLogger.Level.WARN, "正文提取失败 host=$host url=$link")
            return FetchOutcome.Failure(
                FetchFailure.EXTRACT_FAILED,
                FetchReport(
                    link, link, host, statusCode, attempts, 0, elapsed(started),
                    rawHtml.length, 0, null, null, FetchFailure.EXTRACT_FAILED,
                ),
            )
        }

        var contentHtml = first.contentHtml
        var contentText = first.contentText
        var pages = 1
        val seen = paragraphKeys(contentHtml).toMutableSet()
        var nextUrl = nextPageUrl(rawHtml, link)
        // 只在首屏就拿到像样正文时才翻页：首屏都空，翻下去通常是广告页
        while (nextUrl != null && pages < config.maxPages && first.quality.chars >= config.extract.minContentChars) {
            when (val result = download(nextUrl, attempt = 1)) {
                is Download.Ok -> {
                    val page = ArticleExtractor.extract(nextUrl, result.html, config.extract)
                    val added = page?.let { appendPage(contentHtml, it.contentHtml, seen) }
                    if (page == null || added == null) break
                    contentHtml = added
                    contentText = (contentText + "\n" + page.contentText).trim()
                    pages++
                    nextUrl = nextPageUrl(result.html, nextUrl)
                }
                is Download.Err -> {
                    logger.log(FetchLogger.Level.INFO, "分页抓取中断 kind=${result.kind} url=$nextUrl")
                    break
                }
            }
        }

        val stats = measure(contentHtml)
        val content = FetchedContent(
            contentHtml = contentHtml,
            contentText = contentText,
            coverUrl = first.coverUrl,
            title = first.title,
            author = first.author,
            publishedAt = first.publishedAt,
            pages = pages,
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
            pages = pages,
            durationMs = elapsed(started),
            bytes = rawHtml.length,
            contentChars = stats.chars,
            extractor = first.quality.extractor,
            issue = first.quality.issue,
        )
        if (content.isComplete) {
            logger.log(
                FetchLogger.Level.INFO,
                "抓取成功 chars=${stats.chars} pages=$pages extractor=${content.extractor} host=$host",
            )
        } else {
            logger.log(
                FetchLogger.Level.WARN,
                "正文不完整 issue=${content.issue} chars=${stats.chars} paragraphs=${stats.paragraphs} " +
                    "extractor=${content.extractor} pages=$pages host=$host url=$link",
            )
        }
        return FetchOutcome.Success(content, report)
    }

    /** 拼接下一页：按段落文本去重，返回拼接后的 HTML；没有新增段落则 null（防死循环）。 */
    private fun appendPage(current: String, next: String, seen: MutableSet<String>): String? {
        val fresh = Jsoup.parseBodyFragment(next)
            .select("p, h2, h3, li, pre, blockquote")
            .filter { el ->
                val key = el.text().trim().take(80)
                key.isNotEmpty() && seen.add(key)
            }
        if (fresh.isEmpty()) return null
        // 两份片段都已 sanitize，直接拼串（跨 Document 搬节点在 jsoup 里不稳定，绕开）
        return buildString {
            append(current)
            fresh.forEach { append(it.outerHtml()) }
        }
    }

    private fun paragraphKeys(html: String): Set<String> =
        Jsoup.parseBodyFragment(html)
            .select("p, h2, h3, li, pre, blockquote")
            .mapNotNull { it.text().trim().take(80).takeIf { k -> k.isNotEmpty() } }
            .toSet()

    private fun measure(html: String): ContentStats {
        val doc = Jsoup.parseBodyFragment(html)
        return ContentStats(doc.text().length, doc.select("p").size, doc.select("img").size)
    }

    private data class ContentStats(val chars: Int, val paragraphs: Int, val images: Int)

    /**
     * 分页链接识别：`link[rel=next]` → 锚点文案（下一页/next/»…）→ class/id 含 next → `page` 参数 +1。
     * 只认同源、且不等于当前地址的链接。
     */
    internal fun nextPageUrl(html: String, currentUrl: String): String? {
        val doc = runCatching { Jsoup.parse(html, currentUrl) }.getOrNull() ?: return null
        val currentHost = runCatching { java.net.URI(currentUrl).host }.getOrNull() ?: return null

        doc.selectFirst("link[rel=next]")?.absUrl("href")
            ?.takeIf { isFresh(it, currentUrl, currentHost) }?.let { return it }

        for (a in doc.select("a[href]")) {
            val text = a.text().trim()
            if (text.isBlank()) continue
            if (NEXT_TEXTS.any { text.equals(it, ignoreCase = true) }) {
                val url = a.absUrl("href")
                if (isFresh(url, currentUrl, currentHost)) return url
            }
        }
        for (a in doc.select("a[href]")) {
            if (!a.className().contains("next", ignoreCase = true) && !a.id().contains("next", ignoreCase = true)) continue
            val url = a.absUrl("href")
            if (isFresh(url, currentUrl, currentHost)) return url
        }
        return nextPageParam(currentUrl)
    }

    /** 兜底：`?page=2` / `&p=3` 这类纯参数分页，把页码 +1。 */
    private fun nextPageParam(currentUrl: String): String? {
        val uri = runCatching { java.net.URI(currentUrl) }.getOrNull() ?: return null
        val query = uri.rawQuery ?: return null
        for (key in listOf("page", "p", "Page", "pageNum")) {
            val match = Regex("(?:^|&)$key=(\\d+)(?:&|\$)").find(query) ?: continue
            val n = match.groupValues[1].toIntOrNull() ?: continue
            val next = query.replaceRange(match.range, "$key=${n + 1}")
            return runCatching {
                java.net.URI(uri.scheme, uri.authority, uri.path, next, uri.fragment).toString()
            }.getOrNull()
        }
        return null
    }

    private fun isFresh(url: String, currentUrl: String, currentHost: String): Boolean {
        if (url.isBlank() || url == currentUrl || !url.startsWith("http")) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return host.equals(currentHost, ignoreCase = true)
    }

    // ———————————————————————————————————————————————
    // 下载
    // ———————————————————————————————————————————————

    private sealed interface Download {
        data class Ok(val html: String, val status: Int) : Download
        data class Err(val kind: FetchFailure, val status: Int?, val retryAfterMs: Long?) : Download
    }

    private fun download(link: String, attempt: Int): Download {
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
            connection.setRequestProperty("User-Agent", config.userAgents[(attempt - 1) % config.userAgents.size])
            config.extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            runCatching { java.net.URI(link) }.getOrNull()?.let { uri ->
                if (uri.scheme != null && uri.host != null) {
                    connection.setRequestProperty("Referer", "${uri.scheme}://${uri.host}/")
                }
            }

            when (val code = connection.responseCode) {
                in 200..299 -> {
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
            connection.disconnect()
        }
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
        val NEXT_TEXTS = listOf("下一页", "下页", "下一页 »", "下一页>", "Next", "next page", "›", "»", ">")
    }
}

internal fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
