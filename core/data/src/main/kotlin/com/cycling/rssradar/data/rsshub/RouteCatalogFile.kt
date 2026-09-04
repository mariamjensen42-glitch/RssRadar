package com.cycling.rssradar.core.data.rsshub

import kotlinx.serialization.Serializable
import com.cycling.rssradar.core.domain.rsshub.RoutePath
import com.cycling.rssradar.core.model.rsshub.CatalogSource
import com.cycling.rssradar.core.model.rsshub.ParamOption
import com.cycling.rssradar.core.model.rsshub.RouteExample
import com.cycling.rssradar.core.model.rsshub.RouteParam
import com.cycling.rssradar.core.model.rsshub.RouteCatalog
import com.cycling.rssradar.core.model.rsshub.RssHubRoute

/**
 * 路由目录的存储格式（slim schema）。
 *
 * 两个来源共用同一份格式，加载路径只有一条：
 * - 内置快照 `app/src/main/assets/rsshub-routes.json`，由 `scripts/build-route-catalog.py` 生成
 * - 在线更新后的缓存 `files/rsshub-routes.json`，由 [RouteCatalogSlimmer] 生成
 *
 * 字段名刻意压到 1–2 个字符：原始元数据 8.4MB，精简后 ~1.1MB，其中 key 名是大头。
 * schema 表见 ADR-0010；改字段必须三处同步（本文件 / python 脚本 / ADR）。
 */
@Serializable
data class RouteCatalogFile(
    /** schema 版本。不认识的版本按损坏处理，回落到内置快照。 */
    val v: Int,
    /** 数据生成时刻（epoch millis）。 */
    val generatedAt: Long,
    /** 命名空间键 → 命名空间。 */
    val namespaces: Map<String, RouteNamespaceEntry>,
)

@Serializable
data class RouteNamespaceEntry(
    /** 站点中文名，如「哔哩哔哩」。 */
    val n: String = "",
    /** 站点域名，如 `www.bilibili.com`。 */
    val u: String = "",
    /** 分类 key 列表。 */
    val c: List<String> = emptyList(),
    /** 热度。 */
    val h: Long = 0,
    /** 该命名空间下的路由，已按热度降序。 */
    val r: List<RouteEntry> = emptyList(),
)

@Serializable
data class RouteEntry(
    /** path，含命名空间前缀，如 `/bilibili/user/video/:uid/:embed?`。 */
    val p: String,
    /** 路由名。 */
    val n: String,
    /** 热度。 */
    val h: Long = 0,
    /** 分类 key 列表。 */
    val c: List<String> = emptyList(),
    /** 描述（截断）。 */
    val d: String = "",
    /** 参数说明：参数名 → 说明（截断）。 */
    val pm: Map<String, String> = emptyMap(),
    /** 参数可选值：参数名 → 可选值列表。 */
    val po: Map<String, List<RouteParamOptionEntry>> = emptyMap(),
    /** 参数默认值：参数名 → 默认值。 */
    val pd: Map<String, String> = emptyMap(),
    /** 可直接订阅的示例。 */
    val e: List<RouteExampleEntry> = emptyList(),
)

@Serializable
data class RouteParamOptionEntry(
    val v: String,
    val l: String,
)

@Serializable
data class RouteExampleEntry(
    val p: String,
    val t: String = "",
)

/** 目录文件 → 领域模型。路由全局按热度降序，保证「默认展示最热」只需顺序取前 N 条。 */
fun RouteCatalogFile.toCatalog(source: CatalogSource): RouteCatalog {
    val routes = ArrayList<RssHubRoute>(4000)
    namespaces.forEach { (namespace, entry) ->
        val sourceName = entry.n.ifBlank { namespace }
        entry.r.forEach { route ->
            routes.add(
                RssHubRoute(
                    path = route.p,
                    name = route.n,
                    namespace = namespace,
                    sourceName = sourceName,
                    sourceUrl = entry.u,
                    categories = route.c.ifEmpty { entry.c },
                    heat = route.h,
                    description = route.d,
                    params = route.toParams(),
                    examples = route.e.map { RouteExample(path = it.p, title = it.t) },
                ),
            )
        }
    }
    routes.sortByDescending { it.heat }
    return RouteCatalog(routes = routes, generatedAtMillis = generatedAt, source = source)
}

/**
 * 参数表单：顺序以 path 里出现的顺序为准。
 *
 * 3800 条路由里有 93 条元数据的 parameters 与 path 参数名对不上（RSSHub 自身的文档缺陷），
 * 以 path 为准才能保证拼出来的 URL 结构正确；说明缺失只是文案少一行。
 */
private fun RouteEntry.toParams(): List<RouteParam> = RoutePath.params(p).map { spec ->
    val description = pm[spec.key].orEmpty()
    RouteParam(
        key = spec.key,
        label = paramLabel(description, spec.key),
        description = description,
        optional = spec.optional,
        pattern = spec.pattern,
        options = po[spec.key].orEmpty().map { ParamOption(value = it.v, label = it.l) },
        defaultValue = pd[spec.key],
    )
}

/**
 * 字段标签取说明的首句，过长或是 markdown 表格（RSSHub 文档里不少参数说明是表格）
 * 就回落参数名——一个 24 字的表头当标题比直接写 `uid` 更难读。
 */
private fun paramLabel(description: String, key: String): String {
    val first = description
        .split('。', '\n', '；', ';', ',', '，', '｜')
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.trimStart('|', '-', ':', ' ')
        ?.trim()
        .orEmpty()
    return when {
        first.isBlank() -> key
        first.contains('|') -> key
        first.length > 24 -> key
        else -> first
    }
}
