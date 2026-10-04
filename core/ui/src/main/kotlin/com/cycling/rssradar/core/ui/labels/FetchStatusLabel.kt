package com.cycling.rssradar.core.ui.labels

import com.cycling.rssradar.core.model.ExtractionIssue
import com.cycling.rssradar.core.model.FetchFailure
import com.cycling.rssradar.core.ui.R

/**
 * 抓取失败 / 提取问题 → 文案资源（ADR-0017：枚举只给身份，人话由 UI 层按当前语言翻译）。
 *
 * 原先住在 `ui/article/BodyContent.kt` 且是 `internal`，而 `ui/me` 的抓取诊断页也要同一套说法。
 * 两个 feature 一旦分家，「internal 跨模块不可见」+「feature 间禁互依」会同时踩。
 * 这两组映射被 ≥2 个 feature 依赖，按「被依赖 ≥2 次即非私有物」沉到 core.ui.labels；
 * 配套的 18 条 string（`fetch_*` ×12 + `issue_*` ×6）一并进 `core:ui/res`。
 */
fun FetchFailure.uiRes(): Int = when (this) {
    FetchFailure.INVALID_URL -> R.string.fetch_invalid_url
    FetchFailure.TIMEOUT -> R.string.fetch_timeout
    FetchFailure.NETWORK -> R.string.fetch_network
    FetchFailure.HTTP_401 -> R.string.fetch_http_401
    FetchFailure.HTTP_403 -> R.string.fetch_http_403
    FetchFailure.HTTP_404 -> R.string.fetch_http_404
    FetchFailure.HTTP_429 -> R.string.fetch_http_429
    FetchFailure.HTTP_5XX -> R.string.fetch_http_5xx
    FetchFailure.HTTP_OTHER -> R.string.fetch_http_other
    FetchFailure.EMPTY_BODY -> R.string.fetch_empty_body
    FetchFailure.DECODE_ERROR -> R.string.fetch_decode_error
    FetchFailure.EXTRACT_FAILED -> R.string.fetch_extract_failed
}

/** 注意 `NONE` 映射到 issue_site_limit：正文没问题时，头部提示讲的是「站点限制」而不是「一切正常」。 */
fun ExtractionIssue.uiRes(): Int = when (this) {
    ExtractionIssue.NONE -> R.string.issue_site_limit
    ExtractionIssue.TOO_SHORT -> R.string.issue_too_short
    ExtractionIssue.NO_PARAGRAPH -> R.string.issue_no_paragraph
    ExtractionIssue.DYNAMIC_RENDER -> R.string.issue_dynamic_render
    ExtractionIssue.PAYWALL -> R.string.issue_paywall
    ExtractionIssue.METADATA_MISSING -> R.string.issue_metadata_missing
}
