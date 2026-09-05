package com.cycling.rssradar.core.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


/**
 * 产物中心的一行可读文本。
 *
 * @param depth 层级，UI 用它决定缩进（0 为顶层）。
 * @param label 键名（已汉化）；顶层无键的行（如数组元素）为 null。
 */
data class AiPayloadLine(
    val depth: Int,
    val label: String?,
    val value: String,
)


/**
 * 把任意一项 AI 产物的 payload 渲染成**人能读的行**。
 *
 * 为什么需要它：35 项功能各有各的 payload 结构，为每项写一个渲染器要写 35 个，
 * 而且每新增一项功能都要同步补一个。产物中心要的是"让用户看见结果长什么样"，
 * 不是"给每项功能做一套精致排版"。因此这里直接吃 JSON 结构本身——
 * 遍历 JsonElement 树，不认识 payload 的具体类型，新增功能零成本自动覆盖。
 *
 * 两条刻意的取舍：
 * 1. **键名汉化只覆盖真实出现过的字段**，查不到就原样显示英文。
 *    宁可看到一个不认识的 `foo`，也不要为了好看去猜一个可能是错的中文名——
 *    那是把模型的字段名当成可以意译的东西，属于捏造。
 * 2. **解析失败就退化成纯文本**，不抛异常。产物里确实存着纯文本
 *    （摘要、翻译），而且模型偶尔会返回不合规的 JSON——这两种情况都必须显示，
 *    而不是让页面报错。用户宁可看到一段原文，也不想看到"无法显示"。
 */
object AiPayloadText {

    /** 把产物渲染成带层级的行。 */
    fun lines(raw: String): List<AiPayloadLine> {
        val element = parseOrNull(raw)
            ?: return if (raw.isBlank()) emptyList() else listOf(AiPayloadLine(0, null, raw.trim()))
        val out = ArrayList<AiPayloadLine>(24)
        if (element is JsonObject) {
            // 顶层对象的字段就是第 0 层；再走 walk 会被当成"嵌套对象"多加一级缩进。
            element.forEach { (key, child) -> walk(child, depth = 0, label = key, out = out) }
        } else {
            walk(element, depth = 0, label = null, out = out)
        }
        return out
    }

    /** 缩进后的模型原文。结构化与纯文本都用它，用户要能核对"模型到底说了什么"。 */
    fun prettyRaw(raw: String): String {
        val trimmed = raw.trim()
        val element = parseOrNull(trimmed) ?: return trimmed
        return runCatching { PRETTY.encodeToString(JsonElement.serializer(), element) }
            .getOrDefault(trimmed)
    }

    /**
     * 先 [AiParsers.extractJson] 剥掉代码围栏与前后废话，再解析。
     *
     * 复用它是必需的：执行器落库时，只有"引用了文章 id、需要按候选集收口"的产物
     * 会被重编码成规范 JSON，其余功能**直接存模型原文**。模型原文里带
     * ```` ```json ```` 围栏、前后写几句客套话是常态，直接 parse 必然失败，
     * 产物中心就会退化成"一大段纯文本"——结构化展示对这部分功能等于没做。
     */
    private fun parseOrNull(raw: String): JsonElement? {
        val json = AiParsers.extractJson(raw) ?: return null
        return runCatching { AiJson.parseToJsonElement(json) }.getOrNull()
    }

    private val PRETTY = Json { prettyPrint = true }

    private fun walk(element: JsonElement, depth: Int, label: String?, out: MutableList<AiPayloadLine>) {
        when (element) {
            is JsonPrimitive -> {
                val value = renderValue(element.content)
                if (value.isNotEmpty()) out.add(AiPayloadLine(depth, label?.let(::labelOf), value))
            }

            is JsonArray -> {
                // 全是标量的数组并成一行（"标签: a / b / c"），
                // 拆成十行会让一个本来很短的产物变成一整屏。
                if (element.all { it is JsonPrimitive }) {
                    val joined = element.joinToString(" / ") { (it as JsonPrimitive).content }
                    if (joined.isNotEmpty()) out.add(AiPayloadLine(depth, label?.let(::labelOf), joined))
                    return
                }
                element.forEachIndexed { index, child ->
                    if (child is JsonObject) {
                        out.add(AiPayloadLine(depth, null, "第 ${index + 1} 项"))
                        walk(child, depth + 1, null, out)
                    } else {
                        walk(child, depth, label, out)
                    }
                }
            }

            is JsonObject -> {
                if (element.isEmpty()) return
                // 进一层对象就深一级，否则嵌套对象的字段会跟它的父键排在同一列，
                // 看起来像是平级的兄弟字段。
                element.forEach { (key, child) -> walk(child, depth + 1, key, out) }
            }
        }
    }

    private fun renderValue(value: String): String {
        // 只做取值直译，不做任何数值换算——无法确知字段语义（有的 0~1、有的 0~100），
        // 换算错了就是把一个数字变成另一个数字，属于捏造。
        return VALUES[value] ?: value
    }

    private fun labelOf(key: String): String = LABELS[key] ?: key

    /** 字段名汉化表：只收真实出现在 payload 里的键，查不到原样返回。 */
    private val LABELS: Map<String, String> = mapOf(
        "title" to "标题", "headline" to "要闻", "summary" to "摘要", "gist" to "主旨",
        "items" to "条目", "tags" to "标签", "keywords" to "关键词", "topic" to "话题",
        "confidence" to "置信度", "alternatives" to "也可能是", "reason" to "理由",
        "reasons" to "噪声信号", "score" to "强度", "polarity" to "情绪",
        "overall" to "综合", "density" to "信息密度", "originality" to "原创性",
        "evidence" to "证据充分性", "clickbait" to "标题党", "note" to "说明",
        "value" to "信息价值", "isNoise" to "判定为噪声", "keptPoints" to "实质要点",
        "sections" to "章节", "heading" to "小标题", "claims" to "论点",
        "claim" to "论点", "kind" to "类型", "basis" to "依据", "level" to "档位",
        "signals" to "依据", "doubts" to "存疑点", "variants" to "文案",
        "style" to "风格", "text" to "内容", "answer" to "回答",
        "quotes" to "依据段落", "notFound" to "文中未提及", "term" to "术语",
        "explanation" to "解释", "ok" to "是否成功", "html" to "正文",
        "why" to "为什么相关", "articleIds" to "文章", "articleId" to "文章",
        "points" to "要点", "advice" to "建议", "status" to "状态",
        "feeds" to "订阅源", "feedId" to "订阅源", "name" to "名称",
        "url" to "地址", "description" to "说明", "consensus" to "共识",
        "divergence" to "分歧", "watch" to "待观察", "sources" to "来源",
        "timeline" to "时间线", "nodes" to "节点", "date" to "日期",
        "time" to "时间", "label" to "名称", "count" to "数量",
        "total" to "合计", "ratio" to "占比", "interest" to "兴趣",
        "strength" to "强度", "topics" to "话题", "rules" to "规则",
        "pattern" to "匹配式", "examples" to "命中示例", "skippable" to "可跳过",
        "highlights" to "要点", "read" to "已读", "missed" to "遗漏",
        "conclusion" to "结论", "observations" to "观察结论", "gaps" to "盲区话题",
        "similar" to "相似文章", "groupId" to "分组", "primaryId" to "主篇",
    )

    /** 取值直译表：只收代码里真实存在的枚举取值。 */
    private val VALUES: Map<String, String> = mapOf(
        "POSITIVE" to "偏正面", "NEGATIVE" to "偏负面", "NEUTRAL" to "中性",
        "HIGH" to "高", "MEDIUM" to "中", "LOW" to "低",
        "FACT" to "事实", "DATA" to "数据", "OPINION" to "观点",
        "THREAD" to "长推", "BULLET" to "要点体", "SHORT" to "短评",
        "true" to "是", "false" to "否",
    )
}
