package com.cycling.rssradar.core.data.rss

import com.cycling.rssradar.core.domain.rss.HttpFetcher
import com.cycling.rssradar.core.domain.rss.HttpUrlFetcher
import com.cycling.rssradar.core.domain.rss.normalizeHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL

/**
 * 站点图标抓取器（docs/readyou-feature-comparison.md #6，CONTEXT.md「站点图标」）。
 *
 * 名字致敬 ReadYou 的 BestIconFinder，但**不调 Besticon 托管服务**——纯客户端：
 * 抓源站点 HTML，按 apple-touch-icon → rel~=icon 的顺序选第一个候选，
 * 都没有（或 HTML 抓不到）就回落 /favicon.ico。明确排除 og:image——那是内容图不是 logo。
 *
 * 与 ReadYou 的差异（简化选优）：不逐个下载候选验证体积/格式。图标只显示 18-34dp，
 * 任何能加载的候选都够用；Coil 加载失败由 FeedIcon 回落字母占位，选错的代价极低，
 * 而省下的是每个站点 2-4 张候选图的完整下载。
 *
 * 网络走 [HttpFetcher] 缝（与 feed 抓取同一条 seam，UA 常量同源），超时收紧到 5s——
 * 图标是装饰性资产，任何失败静默返回 null，不重试。
 * 测试塞 fake fetcher 即可离线跑 [findIcon] 的选择逻辑。
 */
class BestIconFinder(
    private val http: HttpFetcher = HttpUrlFetcher(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
) {

    /** 返回站点图标的远程 URL；失败返回 null，由调用方放弃（FeedIcon 回落字母占位）。 */
    suspend fun findIcon(siteUrl: String): String? = withContext(Dispatchers.IO) {
        val base = normalizeHttpUrl(siteUrl) ?: return@withContext null
        try {
            fetchHtml(base)
                .let { selectIconUrl(base, it) }
                ?: faviconUrl(base)
        } catch (_: Exception) {
            // HTML 抓不到（反爬 / 超时 / 非 HTML）：仍按约定路径试一次
            faviconUrl(base)
        }
    }

    /**
     * 从 HTML 里选图标 URL（纯函数，单测缝）：apple-touch-icon 优先，
     * 其次 rel~=icon（shortcut icon 也会被词匹配命中）。相对路径按 [baseUrl] 解析为绝对路径。
     */
    internal fun selectIconUrl(baseUrl: String, html: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        doc.selectFirst("link[rel~=apple-touch-icon]")?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        doc.selectFirst("link[rel~=icon]")?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    /** 约定路径兜底（internal 供单测）。 */
    internal fun faviconUrl(baseUrl: String): String =
        URL(URL(baseUrl), "/favicon.ico").toString()

    private fun fetchHtml(url: String): String =
        http.fetch(url).use { it.readBytes().toString(Charsets.UTF_8) }
}
