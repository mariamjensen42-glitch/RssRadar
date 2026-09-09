package com.cycling.rssradar.core.data.update

import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpStatusException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 应用内检查更新（ReadYou 差距表第 35 项）。
 *
 * 三条硬约束：
 * 1. **版本比较不猜**：拆不成整数段就返回「不是更新」——宁可漏报一次，也不把
 *    `1.0-beta` 之类解析成奇怪的数字然后骗用户去升级。
 * 2. **失败要给出人话原因**：网络不可用 / GitHub 回 4xx / 解析不出版本，三种情况
 *    文案不同。统一一句「检查失败」等于没说。
 * 3. **只认 latest release**：不扫全部 releases、不比较发布时间，也不自动下载安装
 *    （装包要过用户，这里只给出去 Release 页的链接）。
 */
const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/mariamjensen42-glitch/RssRadar/releases/latest"

/** 一个可展示的新版本。 */
data class AppRelease(
    /** 原始 tag（带 v 前缀就带，UI 原样展示，不自作主张改格式）。 */
    val version: String,
    /** Release 页面地址：本应用不自己装包，点了交给浏览器。 */
    val url: String,
    val title: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    data class Available(val release: AppRelease) : UpdateCheckResult

    data class Failed(val message: String) : UpdateCheckResult
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val name: String = "",
)

private val ReleaseJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** GitHub Release JSON → [AppRelease]；缺 tag 或链接、或根本不是 JSON 时返回 null（不猜）。 */
fun parseLatestRelease(json: String): AppRelease? =
    runCatching { ReleaseJson.decodeFromString<GitHubRelease>(json) }
        .getOrNull()
        ?.takeIf { it.tagName.isNotBlank() && it.htmlUrl.isNotBlank() }
        ?.let { AppRelease(version = it.tagName, url = it.htmlUrl, title = it.name) }

/**
 * [latest] 是否比 [current] 新。容忍 `v` 前缀，按点分段逐段比数字；
 * 任一段解析不出整数、或位数不齐（1.0 vs 1.0.1）时返回 false——比不出来就说没更新。
 */
fun isNewerVersion(current: String, latest: String): Boolean {
    val a = current.trim().removePrefix("v").removePrefix("V").split('.')
    val b = latest.trim().removePrefix("v").removePrefix("V").split('.')
    val width = maxOf(a.size, b.size)
    for (i in 0 until width) {
        val mine = a.getOrNull(i)?.toIntOrNull() ?: return false
        val theirs = b.getOrNull(i)?.toIntOrNull() ?: return false
        if (theirs > mine) return true
        if (theirs < mine) return false
    }
    return false
}

/** 检查更新：[HttpFetcher] 是注入缝，测试塞固定响应即可离线复现三种结果。 */
class UpdateChecker(
    private val http: HttpFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(ioDispatcher) {
        val raw = try {
            http.fetch(LATEST_RELEASE_URL).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: HttpStatusException) {
            return@withContext UpdateCheckResult.Failed("GitHub 返回 ${e.code}，稍后再试")
        } catch (_: IOException) {
            return@withContext UpdateCheckResult.Failed("网络不可用，检查失败")
        }
        val release = parseLatestRelease(raw)
            ?: return@withContext UpdateCheckResult.Failed("没读到版本号，稍后再试")
        if (!isNewerVersion(currentVersion, release.version)) return@withContext UpdateCheckResult.UpToDate
        UpdateCheckResult.Available(release)
    }
}
