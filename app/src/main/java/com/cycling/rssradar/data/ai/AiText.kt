package com.cycling.rssradar.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI 输入/输出的纯函数集（issue #44 的唯一测试缝）：
 * 语言预检、长文截断标注、DeepSeek 响应解析。全部无副作用，JVM 可测。
 */
object AiText {

    /**
     * 正文里中文字符占比：中文字符数 / 全部字母数。没有字母（空串、纯数字）返回 0.0。
     * 供翻译预检使用——中文文章不调 API，省真金白银（issue #44 决策）。
     */
    fun chineseCharRatio(text: String): Double {
        var chinese = 0
        var letters = 0
        for (c in text) {
            if (c.isLetter()) {
                letters++
                if (c in '\u4E00'..'\u9FFF') chinese++
            }
        }
        if (letters == 0) return 0.0
        return chinese.toDouble() / letters
    }

    /** 中文占比超阈值 → 视为中文文章。 */
    fun isMostlyChinese(text: String): Boolean =
        chineseCharRatio(text) > CHINESE_RATIO_THRESHOLD

    /**
     * 把输入截到 [maxChars] 内，返回（截断后的文本，是否发生了截断）。
     * 极端长文超 DeepSeek 上下文前的保护性截断。
     */
    fun truncateForPrompt(text: String, maxChars: Int = MAX_INPUT_CHARS): Pair<String, Boolean> =
        if (text.length <= maxChars) text to false else text.take(maxChars) to true

    /**
     * 截断标注：摘要尾部注明「基于前 N 字」。
     * AI 不捏造原则的延伸——不假装读了全文。
     */
    fun truncationNote(charCount: Int): String =
        "（原文较长，本摘要基于前 $charCount 字）"

    /**
     * 解析 /chat/completions 响应，取 `choices[0].message.content`。
     * 结构不符、内容为空、非法 JSON 一律返回 null（调用方转为失败），
     * 对 DeepSeek 返回体里的额外字段保持宽容。
     */
    fun parseChatCompletion(json: String): String? = try {
        val root = Json.parseToJsonElement(json).jsonObject
        val content = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
        content?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** 中文判定阈值：字母里中文字符占比 > 30% 视为中文文章。 */
    const val CHINESE_RATIO_THRESHOLD = 0.3

    /** 输入字符上限（≈2 万 token，留足输出余量）。 */
    const val MAX_INPUT_CHARS = 30_000
}
