package com.cycling.rssradar.ui.article

import com.cycling.rssradar.data.store.ReadingRenderer

/**
 * 正文渲染模式：阅读页正文槽位五条渲染路径的唯一判据。
 *
 * 此前这五路分支的判据是 [ReadingBody] 里八个派生 val 隐式拼出来的，埋在
 * Composable 内因此一条都测不到。抽成本枚举 + [resolveBodyPlan] 后，
 * 「什么情况下走哪条路」成为可 JVM 断言的纯函数。
 */
internal enum class BodyMode {
    /** 译文：原生分段渲染（渐进显示 + 双语对照，翻译功能 v2）。 */
    TRANSLATION,

    /**
     * 译文兜底：译文分段全部解析不出内容（怪 HTML），退回整页 WebView 显示
     * 已完成译文（或原文）——宁可退化，也不渲染成空白页。
     */
    TRANSLATION_FALLBACK,

    /** 原生渲染器（ADR-0009）：中间树非空即走 Compose。 */
    NATIVE,

    /** 订阅源自带正文（或按需抓来的正文）：WebView。 */
    WEBVIEW,

    /** 无正文：只显示摘要与「抓取中」提示。 */
    NO_CONTENT,
}

/**
 * 正文渲染计划：[mode] 加上该模式需要的、与模式一起算出来的产物。
 *
 * 中间树与兜底 HTML 一并给出，是为了**只解析一次**——判据本身就要求解析 HTML
 * 才能知道「解析一无所获」，分开算就是同一份 HTML 解析两遍。
 */
internal data class BodyPlan(
    val mode: BodyMode,
    /** [BodyMode.NATIVE] 的中间树；其余模式为空。 */
    val nativeNodes: List<ReadingNode> = emptyList(),
    /** [BodyMode.TRANSLATION_FALLBACK] 的兜底 HTML；其余模式为 null。 */
    val fallbackHtml: String? = null,
)

/**
 * 决定正文走哪条渲染路径。纯函数：无 Compose、无 Android 依赖，可 JVM 单测。
 *
 * 判据（顺序即优先级）：
 * 1. 翻译激活（渐进中或已完成）→ 原生分段渲染；但若所有分段都解析不出内容，
 *    退回 WebView 显示译文拼接（或原文），绝不渲染空白页。
 * 2. 无正文 → 只显示摘要。
 * 3. 渲染器选原生 **且** 解析出的中间树非空 → 原生 Compose 路。
 *    空树 = 解析一无所获，此时自动退回 WebView（[ReadingNodes.parse] 会吞掉一切异常）。
 * 4. 其余 → WebView。
 *
 * @param translationActive 翻译过程态为 Progressing 或 Shown。
 * @param renderer 用户选择的正文渲染器（阅读偏好）。
 */
internal fun resolveBodyPlan(
    translationActive: Boolean,
    translationSegments: List<TranslationSegmentUi>,
    content: String?,
    summary: String?,
    renderer: ReadingRenderer,
): BodyPlan {
    if (translationActive) {
        // 每个分段取「有译文用译文，否则原文」判断能不能渲染出东西
        val allBlank = translationSegments.isNotEmpty() &&
            translationSegments.all { segment ->
                val html = segment.translatedHtml ?: segment.originalHtml
                html.isBlank() || ReadingNodes.parse(html).isEmpty()
            }
        if (!allBlank) return BodyPlan(BodyMode.TRANSLATION)
        // 译文一条都渲染不出来：退到已完成译文的拼接，再不济退回原文/摘要
        val joined = translationSegments.mapNotNull { it.translatedHtml }
            .joinToString("")
            .ifBlank { null }
        return BodyPlan(
            mode = BodyMode.TRANSLATION_FALLBACK,
            fallbackHtml = joined ?: content ?: summary,
        )
    }
    if (content == null) return BodyPlan(BodyMode.NO_CONTENT)
    if (renderer == ReadingRenderer.NATIVE) {
        val nodes = ReadingNodes.parse(content)
        if (nodes.isNotEmpty()) return BodyPlan(BodyMode.NATIVE, nativeNodes = nodes)
    }
    return BodyPlan(BodyMode.WEBVIEW)
}

/**
 * 是否走「视口渲染」——正文 WebView 高度固定、内部滚动（ADR-0007 的 OOM 防线）。
 *
 * 只有 [BodyMode.WEBVIEW] 且正文含图时才成立：有图的整页包高 WebView 会被 Chromium
 * 视为全部内容可见，所有图片同时解码进 Java 堆，图多必 OOM。原生路与译文路是
 * Compose 渲染，没有这个约束；纯文字的 WebView 栅格内存可控，也不必走视口。
 */
internal fun shouldUseViewport(mode: BodyMode, content: String?): Boolean =
    mode == BodyMode.WEBVIEW && content.orEmpty().contains("<img", ignoreCase = true)
