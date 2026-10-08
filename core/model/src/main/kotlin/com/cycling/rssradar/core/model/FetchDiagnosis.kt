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
    /** 响应不是网页（图片/PDF/压缩包等）：链接本身即资源的源会走到这里（如必应每日壁纸）。 */
    NOT_HTML,
    ;

    /** 是否值得重试：401/403/404 重试无意义，只会浪费配额并招致更狠的封禁。 */
    val retryable: Boolean
        get() = this == TIMEOUT || this == NETWORK || this == HTTP_429 || this == HTTP_5XX
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
    /** 正文够长，但一半以上是链接文字：抓到的是索引/导航页而不是正文。 */
    LINK_LIST,
    ;
}
