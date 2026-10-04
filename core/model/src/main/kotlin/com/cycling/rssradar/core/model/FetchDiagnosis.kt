package com.cycling.rssradar.core.model

/**
 * 抓取/提取失败的原因分类（诊断页按此归类）。
 *
 * 原住 core.data.parser：uiRes()（枚举 → 文案资源）被 feature:article 与 feature:me 同时依赖，
 * 而 core:ui 铁律禁依赖 core:data ⇒ 两个纯枚举上移 core:model，uiRes() 才落得进 core.ui.labels。
 * 与 AiFeature/AiScope 上移 core:model 是同一手法。
 */
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

/** 质量问题分类（对应 [FetchFailure] 里 EXTRACT_* 的细分，用于日志与诊断页归因）。 */
enum class ExtractionIssue {
    NONE,
    /** 正文过短（低于抽取配置的最小正文字数）。 */
    TOO_SHORT,
    /** 一个段落都没有：基本可以断定容器误判。 */
    NO_PARAGRAPH,
    /** 正文空/极短且页面是 JS 空壳（#app/#root/React 容器 + 大量脚本）。 */
    DYNAMIC_RENDER,
    /** 正文短且命中付费墙/登录墙特征。 */
    PAYWALL,
    /** 正文够长但没有标题或时间（只告警，不算不完整）。 */
    METADATA_MISSING,
    ;

    /**
     * 阅读页要说人话：诊断页看枚举名就够了，读者看不懂 `DYNAMIC_RENDER`。
     * 与 [FetchFailure.label] 同源同类，都属于「失败必须可见」那一类文案。
     */
    val label: String
        get() = when (this) {
            NONE -> "正文完整"
            TOO_SHORT -> "正文过短"
            NO_PARAGRAPH -> "没找到正文段落"
            DYNAMIC_RENDER -> "页面由脚本动态渲染"
            PAYWALL -> "疑似付费墙或登录墙"
            METADATA_MISSING -> "缺少标题或发布时间"
        }
}
