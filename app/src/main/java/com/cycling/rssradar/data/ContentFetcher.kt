package com.cycling.rssradar.data

import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.text.Charsets

/** 抓取结果。 */
data class FetchedContent(
    /** 提取出的正文 HTML（readability 清洗过）。 */
    val contentHtml: String,
    val contentText: String,
    /** 原网页 og:image（仅在已抓原文时顺带取得，见 ADR-0001）。 */
    val coverUrl: String?,
)

/**
 * 按需抓取原网页并提取正文。依据 `docs/adr/0001`：
 * - 提取用 readability4j（Mozilla Readability 算法的 JVM 实现，ReadYou 生产验证）。
 *   Python 验证：抓取成功条件下提取成功率 86%（失败 = 反爬 / JS 渲染页），
 *   因此失败是常态，调用方必须静默降级，不得弹错误。
 * - 结果按 SHA-256(link) 存文件缓存（cacheDir/content/），不进数据库，
 *   命中即不再发起网络请求。
 */
class ContentFetcher(
    private val cacheDir: File,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) {

    suspend fun fetch(link: String): FetchedContent? = withContext(ioDispatcher) {
        val cacheFile = cacheFileFor(link)
        if (cacheFile.exists()) {
            return@withContext extract(cacheFile.readText(), link)
        }
        val html = download(link) ?: return@withContext null
        extract(html, link)?.also { fetched ->
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(fetched.contentHtml)
            }
        }
    }

    private fun cacheFileFor(link: String): File =
        File(File(cacheDir, CACHE_DIR_NAME), link.sha256() + ".html")

    /** 提取失败返回 null，不缓存——下次打开会重试。 */
    private fun extract(html: String, link: String): FetchedContent? = try {
        val article = Readability4J(link, html).parse()
        val contentHtml = article.content?.takeIf { it.isNotBlank() } ?: return null
        val contentText = article.textContent?.takeIf { it.isNotBlank() }
            ?: RssParser.toPlainText(contentHtml)
            ?: return null
        FetchedContent(contentHtml, contentText, extractOgImage(html))
    } catch (_: Exception) {
        null
    }

    private fun download(link: String): String? = try {
        val connection = URL(link).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            null
        } else {
            connection.inputStream.use { stream -> stream.readBytes().decodeWithCharset(connection.contentType) }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * 编码探测：优先 Content-Type 头，其次 HTML meta charset（GBK 页面实测存在，
     * ReadYou 也做了同样处理），兜底 UTF-8。
     */
    private fun ByteArray.decodeWithCharset(contentType: String?): String {
        val headerCharset = contentType?.let {
            Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        val charsetName = headerCharset
            ?: this.decodeToString(0, minOf(size, 2048)).let { head ->
                Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            }
        return runCatching { toString(charset(charsetName ?: "UTF-8")) }
            .getOrDefault(toString(Charsets.UTF_8))
    }

    private fun extractOgImage(html: String): String? {
        val patterns = listOf(
            Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { re ->
            re.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val CACHE_DIR_NAME = "content"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val USER_AGENT = "Mozilla/5.0 (Android) RssRadar/1.0"
    }
}
