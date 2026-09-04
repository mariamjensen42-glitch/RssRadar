package com.cycling.rssradar.core.domain.rsshub

import java.net.URLEncoder

/**
 * RSSHub 路由 path 的参数语法：解析 / 拼接 / 反填。
 *
 * 语法（按 docs.rsshub.app/routes.json 的 3800 条真实 path 归纳）：
 * ```
 * /a/b                字面段
 * /a/:key             必填参数
 * /a/:key?            可选参数
 * /a/:key{[0-9]{2}}   带正则约束（花括号可嵌套）
 * /a/:key{[0-9]+}?    约束 + 可选
 * /a/:category{.+}    约束允许跨路径段（值里的 `/` 有意义，不能编码）
 * ```
 *
 * 全部为纯函数，不联网、不碰 Android API，可直接在 JVM 上单测。
 */
object RoutePath {

    data class ParamSpec(
        val key: String,
        val optional: Boolean = false,
        /** 正则约束原文（不含花括号）；无约束时为 null。 */
        val pattern: String? = null,
    ) {
        /**
         * 约束是否允许跨路径段。`:category{.+}` 这类参数的真实值形如 `sy/gzdt_210283`，
         * 编码掉 `/` 会让 RSSHub 匹配不到路由。
         */
        val spansSegments: Boolean
            get() = pattern != null && (pattern.contains(".+") || pattern.contains(".*"))
    }

    /**
     * 按出现顺序解析出参数——与表单字段顺序一致。
     * 同名参数只保留第一个：path 里重复出现同一个 key 只会让表单多出一个无意义的字段。
     */
    fun params(path: String): List<ParamSpec> {
        val specs = segments(path).mapNotNull { it as? Segment.Param }.map { it.spec }
        val seen = HashSet<String>()
        return specs.filter { seen.add(it.key) }
    }

    /**
     * 把参数值填进 path，返回可直接拼在实例地址后面的相对路径。
     *
     * - 必填参数缺失 → null（调用方据此禁用「生成预览」）
     * - 可选参数缺失 → 整段删除（`/zhihu/hot/:category?` 不填即 `/zhihu/hot`）
     */
    fun build(path: String, values: Map<String, String>): String? {
        val out = ArrayList<String>(path.split('/').size)
        for (segment in segments(path)) {
            when (segment) {
                is Segment.Literal -> out.add(segment.value)
                is Segment.Param -> {
                    val raw = values[segment.spec.key]?.trim().orEmpty()
                    if (raw.isEmpty()) {
                        if (!segment.spec.optional) return null
                    } else {
                        out.add(if (segment.spec.spansSegments) raw.trim('/') else encodeSegment(raw))
                    }
                }
            }
        }
        return out.joinToString("/")
    }

    /**
     * 用一条真实示例 path 反填参数值，供「点示例即用」把表单填满。
     *
     * 模板与示例结构对不上（字面段不一致、必填参数取不到值）时返回 null——
     * 宁可让用户手填，也不要拼出一个似是而非的 URL。
     */
    fun match(path: String, example: String): Map<String, String>? {
        val template = segments(path)
        val actual = example.split('/')
        val values = LinkedHashMap<String, String>()
        var t = 0
        var a = 0
        while (t < template.size) {
            when (val segment = template[t]) {
                is Segment.Literal -> {
                    if (a >= actual.size || actual[a] != segment.value) return null
                    a++
                    t++
                }

                is Segment.Param -> if (segment.spec.spansSegments) {
                    // 通配参数吃掉若干段，直到模板中下一个字面段出现的位置
                    val nextLiteral = template.subList(t + 1, template.size)
                        .firstOrNull { it is Segment.Literal } as? Segment.Literal
                    val end = if (nextLiteral == null) {
                        actual.size
                    } else {
                        val offset = actual.subList(a, actual.size).indexOf(nextLiteral.value)
                        if (offset < 0) return null
                        a + offset
                    }
                    if (end <= a) return null
                    values[segment.spec.key] = actual.subList(a, end).joinToString("/")
                    a = end
                    t++
                } else {
                    if (a >= actual.size) {
                        if (!segment.spec.optional) return null
                        t++
                        continue
                    }
                    values[segment.spec.key] = actual[a]
                    a++
                    t++
                }
            }
        }
        if (a != actual.size) return null
        return values
    }

    /* ------------------------------ 内部 ------------------------------ */

    private sealed interface Segment {
        data class Literal(val value: String) : Segment
        data class Param(val spec: ParamSpec) : Segment
    }

    private fun segments(path: String): List<Segment> = path.split('/').map { raw ->
        if (!raw.startsWith(":") || raw.length < 2) return@map Segment.Literal(raw)

        val body = raw.substring(1)
        val nameEnd = body.indexOfFirst { it == '?' || it == '{' }.let { if (it < 0) body.length else it }
        val key = body.substring(0, nameEnd)
        var rest = body.substring(nameEnd)

        var pattern: String? = null
        if (rest.startsWith("{")) {
            var depth = 0
            var close = -1
            for (i in rest.indices) {
                when (rest[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            close = i
                            break
                        }
                    }
                }
            }
            if (close > 0) {
                pattern = rest.substring(1, close)
                rest = rest.substring(close + 1)
            }
        }
        Segment.Param(ParamSpec(key = key, optional = rest.startsWith("?"), pattern = pattern))
    }

    /** 只编码参数值，path 自身的 `/` 结构必须原样保留。 */
    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
