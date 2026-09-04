package com.cycling.rssradar.core.data.rsshub

import com.cycling.rssradar.core.domain.rsshub.RoutePath
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

/**
 * RSSHub 官方路由元数据的原始格式（docs.rsshub.app/routes.json）。
 *
 * 只声明用得到的字段，其余（maintainers / location / features / radar / test…）靠
 * [REMOTE_JSON] 的 ignoreUnknownKeys 忽略。原始文件 8.4MB，只在「更新目录」时读一次，
 * 读完立刻精简成 [RouteCatalogFile] 落盘。
 */
@Serializable
data class RemoteNamespace(
    val name: String = "",
    val url: String? = null,
    val categories: List<String> = emptyList(),
    val heat: Long = 0,
    val routes: Map<String, RemoteRoute> = emptyMap(),
)

@Serializable
data class RemoteRoute(
    val name: String = "",
    val heat: Long = 0,
    val categories: List<String> = emptyList(),
    val description: String? = null,
    val example: String? = null,
    /** 值形态不统一：多数是纯字符串，少数是 { description, default, options }。 */
    val parameters: Map<String, JsonElement> = emptyMap(),
    /** 结构为 [命名空间, path, [feed…]]——混合类型数组，只能按位取。 */
    val topFeeds: List<JsonElement> = emptyList(),
)

@Serializable
data class RemoteTopFeed(
    val url: String = "",
    val title: String? = null,
    val errorMessage: String? = null,
)

@Serializable
private data class RemoteParamDetail(
    val description: String? = null,
    val default: JsonPrimitive? = null,
    val options: List<RemoteParamOption> = emptyList(),
)

@Serializable
private data class RemoteParamOption(
    val value: JsonPrimitive? = null,
    val label: JsonPrimitive? = null,
)

/** 原始元数据的解析器。宽松模式：字段增删不该让目录更新整体失败。 */
val REMOTE_JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** 目录文件（slim schema）的解析器。 */
val CATALOG_JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@OptIn(ExperimentalSerializationApi::class)
fun decodeRemoteCatalog(stream: InputStream): Map<String, RemoteNamespace> =
    REMOTE_JSON.decodeFromStream(stream)

@OptIn(ExperimentalSerializationApi::class)
fun decodeCatalogFile(stream: InputStream): RouteCatalogFile =
    CATALOG_JSON.decodeFromStream(stream)

/**
 * 原始元数据 → slim schema。
 *
 * 逻辑必须与 `scripts/build-route-catalog.py` 逐条对应（截断上限、markdown 压平、
 * 示例挑选顺序），否则内置快照与在线更新会得到两份不一致的数据。
 * 常量与 python 脚本同名同值，改一处必须改两处。
 *
 * 实测（2026-08-30，1979 命名空间 / 3800 路由）：两边输出在结构与关键字段上完全一致，
 * 仅 13 条路由的 `d`（描述）有字符级差异——两个正则引擎压平 markdown 表格时的边界行为不同。
 * 描述只是辅助文案，不影响目录功能，不值得为它继续对齐。
 */
object RouteCatalogSlimmer {

    private const val DESC_LIMIT = 160
    private const val PARAM_DESC_LIMIT = 100
    private const val TITLE_LIMIT = 60
    private const val SOURCE_NAME_LIMIT = 60
    private const val ROUTE_NAME_LIMIT = 80
    private const val OPTION_LABEL_LIMIT = 40
    private const val TOP_FEEDS_LIMIT = 3
    private const val OPTIONS_LIMIT = 12

    private const val RSSHUB_SCHEME = "rsshub://"

    private val NEWLINES = Regex("[\r\n]+")
    private val MARKDOWN_NOISE = Regex("[`*_>#]+")
    /** RSSHub 文档爱用 `::: warning` / `::: tip` 提示块，压平后只剩一行噪音。 */
    private val MARKDOWN_FENCE = Regex(":::\\s*\\w*")
    private val MARKDOWN_LINK = Regex("!?\\[([^]]*)]\\([^)]*\\)")
    private val MULTI_SPACE = Regex("\\s{2,}")

    fun slim(raw: Map<String, RemoteNamespace>, nowMillis: Long): RouteCatalogFile {
        val namespaces = LinkedHashMap<String, RouteNamespaceEntry>(raw.size)
        raw.forEach { (namespaceKey, namespace) ->
            val routes = namespace.routes.map { (path, route) -> route.slim(path) }
                .sortedByDescending { it.h }
            namespaces[namespaceKey] = RouteNamespaceEntry(
                n = flatten(namespace.name.ifBlank { namespaceKey }, SOURCE_NAME_LIMIT),
                u = namespace.url.orEmpty(),
                c = namespace.categories,
                h = namespace.heat,
                r = routes,
            )
        }
        return RouteCatalogFile(v = 1, generatedAt = nowMillis, namespaces = namespaces)
    }

    private fun RemoteRoute.slim(path: String): RouteEntry {
        // 用 LinkedHashMap 而非 HashMap：字段顺序要跟 python 脚本的输出一致，
        // 否则内置快照与在线更新两份数据 diff 起来全是噪音
        val descriptions = LinkedHashMap<String, String>()
        val optionMap = LinkedHashMap<String, List<RouteParamOptionEntry>>()
        val defaults = LinkedHashMap<String, String>()

        RoutePath.params(path).forEach { spec ->
            val detail = parameters[spec.key]?.detail()
            if (detail != null) {
                val description = flatten(detail.description.orEmpty(), PARAM_DESC_LIMIT)
                if (description.isNotEmpty()) descriptions[spec.key] = description
                val options = detail.options.take(OPTIONS_LIMIT).mapNotNull { option ->
                    val value = option.value?.contentOrNull ?: return@mapNotNull null
                    val label = option.label?.contentOrNull?.takeIf { it.isNotBlank() } ?: value
                    RouteParamOptionEntry(v = value, l = flatten(label, OPTION_LABEL_LIMIT))
                }
                if (options.isNotEmpty()) optionMap[spec.key] = options
                detail.default?.contentOrNull?.takeIf { it.isNotBlank() }?.let { defaults[spec.key] = it }
            } else if (parameters[spec.key] is JsonPrimitive) {
                val description = flatten(parameters.getValue(spec.key).jsonPrimitive.content, PARAM_DESC_LIMIT)
                if (description.isNotEmpty()) descriptions[spec.key] = description
            }
        }

        return RouteEntry(
            p = path,
            n = flatten(name.ifBlank { path }, ROUTE_NAME_LIMIT),
            h = heat,
            c = categories,
            d = flatten(description.orEmpty(), DESC_LIMIT),
            pm = descriptions,
            po = optionMap,
            pd = defaults,
            e = examples(path),
        )
    }

    /**
     * 示例订阅：优先实例上真实被订阅的 topFeeds（带标题、更接近真实用法），
     * 抓取报错的排在健康示例后面；一条都没有才退回官方 example。
     */
    private fun RemoteRoute.examples(path: String): List<RouteExampleEntry> {
        val feeds = topFeeds.getOrNull(2)?.jsonArray ?: emptyList()
        val healthy = ArrayList<RouteExampleEntry>()
        val broken = ArrayList<RouteExampleEntry>()
        feeds.forEach { element ->
            val feed = runCatching { REMOTE_JSON.decodeFromJsonElement<RemoteTopFeed>(element) }.getOrNull()
                ?: return@forEach
            val url = feed.url
            if (!url.startsWith(RSSHUB_SCHEME)) return@forEach
            val entry = RouteExampleEntry(
                p = "/" + url.removePrefix(RSSHUB_SCHEME).trimStart('/'),
                t = flatten(feed.title.orEmpty(), TITLE_LIMIT),
            )
            if (feed.errorMessage.isNullOrBlank()) healthy.add(entry) else broken.add(entry)
        }
        val picked = (healthy + broken).take(TOP_FEEDS_LIMIT)
        if (picked.isNotEmpty()) return picked
        val example = example?.takeIf { it.startsWith("/") } ?: return emptyList()
        return listOf(RouteExampleEntry(p = example))
    }

    private fun JsonElement.detail(): RemoteParamDetail? =
        if (this is JsonObject) runCatching { REMOTE_JSON.decodeFromJsonElement<RemoteParamDetail>(this) }.getOrNull()
        else null

    /** 压平 markdown 噪声并截断；与 python 版 flatten() 同规则。 */
    private fun flatten(text: String, limit: Int): String {
        if (text.isBlank()) return ""
        var s = text.replace("\\n", " ")
        s = NEWLINES.replace(s, " ")
        s = MARKDOWN_NOISE.replace(s, "")
        s = MARKDOWN_FENCE.replace(s, "")
        s = MARKDOWN_LINK.replace(s, "$1")
        s = MULTI_SPACE.replace(s, " ").trim()
        return if (s.length > limit) s.take(limit).trimEnd() + "…" else s
    }
}
