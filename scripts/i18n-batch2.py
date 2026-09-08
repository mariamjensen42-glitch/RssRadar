#!/usr/bin/env python3
"""i18n 批2：ui/article 阅读域文案资源化（ADR-0017）。

- 精确字面量替换为 stringResource(R.string.key)；模板串按显式规则改写。
- 每条替换断言命中次数，未命中即报错退出（防静默漏改）。
- 生成中英 strings 条目追加进 values/strings.xml 与 values-en/strings.xml。
- 不在本批：ReadingDenoise.kt（正文降噪匹配规则，匹配中文网页内容）、
  ReadingNodes.kt（纯 JVM 数据层，占位标签要过 WebView HTML，批6 专项）、
  ArticleDetailViewModel 的 aiMessage（VM 文案，批6 专项）。
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIR = ROOT / "app/src/main/java/com/cycling/rssradar/ui/article"

STRINGS = [
    # (key, 中文, English)
    ("ai_analysis", "AI 分析", "AI Analysis"),
    ("ai_on_demand_hint", "按需生成，生成后保存在本地，刷新不会覆盖。", "Generated on demand and saved locally; refreshing won\\'t overwrite it."),
    ("ai_empty", "还没有生成任何分析，点上面的按钮试试", "No analysis yet — try the button above"),
    ("ai_feature_disabled", "%1$s · 未开启", "%1$s · Disabled"),
    ("ai_key_missing", "未配置 API Key", "API key not configured"),
    ("ai_key_needed_hint", "AI 功能需要你自己的 DeepSeek Key。到「我的 → AI 与诊断」里填入后即可使用。", "AI features need your own DeepSeek key. Set it in Me → AI &amp; Diagnostics."),
    ("ai_ask_hint", "问这篇文章…", "Ask about this article…"),
    ("ai_thinking", "思考中", "Thinking"),
    ("ai_ask", "提问", "Ask"),
    ("ai_term_hint", "也可以输入一个术语，解释它在本文中的含义", "Or enter a term to see what it means in this article"),
    ("ai_verifying", "查证中", "Verifying"),
    ("ai_explain_term", "解释术语", "Explain term"),
    ("ai_topic_confidence", "%1$s  ·  置信度 %2$d%%", "%1$s  ·  Confidence %2$d%%"),
    ("ai_alternatives", "也可能是：%1$s", "Could also be: %1$s"),
    ("ai_sentiment_positive", "偏正面", "Slightly positive"),
    ("ai_sentiment_negative", "偏负面", "Slightly negative"),
    ("ai_sentiment_neutral", "中性", "Neutral"),
    ("ai_sentiment_score", "%1$s  ·  强度 %2$d%%", "%1$s  ·  Intensity %2$d%%"),
    ("ai_overall", "综合 %1$s / 100", "Overall %1$s / 100"),
    ("ai_info_density", "信息密度", "Information density"),
    ("ai_originality", "原创性", "Originality"),
    ("ai_evidence", "证据充分性", "Evidence quality"),
    ("ai_clickbait", "标题党程度", "Clickbait level"),
    ("ai_value", "信息价值 %1$s / 100", "Information value %1$s / 100"),
    ("ai_noise", "噪声信号", "Noise signals"),
    ("ai_key_points", "实质要点", "Substantive points"),
    ("ai_fact", "事实", "Fact"),
    ("ai_data", "数据", "Data"),
    ("ai_opinion", "观点", "Opinion"),
    ("ai_claim_basis", "依据：%1$s", "Basis: %1$s"),
    ("ai_signal_strong", "信号较强", "Strong signal"),
    ("ai_signal_medium", "信号一般", "Moderate signal"),
    ("ai_signal_weak", "信号较弱", "Weak signal"),
    ("ai_signal_insufficient", "信息不足", "Insufficient information"),
    ("ai_doubts", "存疑点：%1$s", "Doubts: %1$s"),
    ("ai_style_long", "长推", "Long-form"),
    ("ai_style_bullets", "要点体", "Bullet points"),
    ("ai_style_short", "短评", "Short take"),
    ("ai_basis", "依据", "Basis"),
    ("ai_not_in_article", "文中未提及", "Not mentioned in the article"),
    ("ai_no_content", "未能从该页面提取到正文", "Couldn\\'t extract the article body from this page"),
    ("ai_fulltext_done", "已提取 %1$d 字并写入正文，向上滚动即可阅读", "Extracted %1$d characters into the body — scroll up to read"),
    ("article_not_found", "文章不存在", "Article not found"),
    ("nav_back", "返回", "Back"),
    ("ai_gen_summary", "生成 AI 摘要", "Generate AI summary"),
    ("ai_back_to_original", "切回原文", "Back to original"),
    ("ai_translate", "AI 翻译", "AI translation"),
    ("more_actions", "更多操作", "More actions"),
    ("share", "分享", "Share"),
    ("typography_settings", "排版设置", "Typography settings"),
    ("body_section", "本文", "Body"),
    ("body_full", "正文", "Full text"),
    ("body_summary", "摘要", "Summary"),
    ("summary_hint", "摘要 = 订阅源自带的简介。仅对本文生效，换一篇自动恢复正文。", "Summary is the feed-provided description. Applies to this article only; opening another article restores the full text."),
    ("reading_theme", "阅读主题", "Reading theme"),
    ("reading_theme_hint", "只换背景与文字，强调色仍用应用配色；选定后固定，不随系统深浅变化。", "Changes background and text only — the accent stays with app colors. Fixed once chosen; doesn\\'t follow system light/dark."),
    ("body_renderer", "正文渲染器", "Body renderer"),
    ("font_size", "字号", "Font size"),
    ("font_decrease", "减小字号", "Smaller font"),
    ("font_increase", "增大字号", "Larger font"),
    ("line_height", "行距", "Line height"),
    ("margin", "边距", "Margin"),
    ("letter_spacing", "字间距", "Letter spacing"),
    ("text_align", "正文对齐", "Text alignment"),
    ("images", "图片", "Images"),
    ("corner_radius", "圆角", "Corner radius"),
    ("tap_to_zoom", "点击放大", "Tap to zoom"),
    ("immersive_mode", "沉浸模式", "Immersive mode"),
    ("immersive_hint", "隐藏分享按钮、推荐阅读、评论区等杂乱内容", "Hides share button, related reads, comments and other clutter"),
    ("auto_hide_bars", "滚动时自动隐藏工具栏", "Auto-hide toolbars while scrolling"),
    ("auto_hide_hint", "下滚收起顶栏与底栏，上滚或回到顶部时重新出现", "Top and bottom bars collapse on scroll down and reappear on scroll up or at the top"),
    ("pull_switch", "下拉 / 上拉切换上下篇", "Pull down/up to switch article"),
    ("pull_switch_hint", "顶部下拉看上一篇，底部上拉看下一篇；只在整页滚动模式生效", "Pull down at the top for the previous article, pull up at the bottom for the next; full-page scroll mode only"),
    ("prev_article", "上一篇", "Previous"),
    ("next_article", "下一篇", "Next"),
    ("star", "收藏", "Star"),
    ("read_later", "稍后读", "Read later"),
    ("view_original", "查看原文", "View original"),
    ("related_reads", "相关阅读", "Related reads"),
    ("article_reading_time", "阅读约 %1$d 分钟", "About %1$d min read"),
    ("article_fetch_failed", "没能取到正文：%1$s", "Couldn\\'t fetch the body: %1$s"),
    ("article_fetch_feed_disabled", "该订阅源关闭了正文抓取，只显示订阅源自带内容", "This feed disabled full-text fetching; showing feed-provided content only"),
    ("article_fetch_shorter", "抓到的正文比现有内容还短（%1$d 字 < %2$d 字），未覆盖", "Fetched body is shorter than existing (%1$d &lt; %2$d chars); not overwritten"),
    ("retry", "重试", "Retry"),
    ("summary_banner", "当前显示订阅源摘要，不是全文", "Showing the feed-provided summary, not the full text"),
    ("see_full", "看正文", "See full text"),
    ("article_incomplete", "正文可能不完整（%1$s），可查看原文", "Body may be incomplete (%1$s) — you can view the original"),
    ("issue_site_limit", "站点限制或动态加载", "Site restriction or dynamic loading"),
    ("body_empty", "本文没有可显示的正文，可查看原文。", "This article has no displayable body — you can view the original."),
    ("fetching_full", "正在获取全文…", "Fetching full text…"),
    ("ai_summary", "AI 摘要", "AI summary"),
    ("generating_summary", "正在生成摘要…", "Generating summary…"),
    ("regenerate", "重新生成", "Regenerate"),
    ("collapse", "收起", "Collapse"),
    ("expand_full", "展开全文", "Expand full text"),
    ("article_translating_progress", "翻译中 %1$d/%2$d 段", "Translating %1$d/%2$d segments"),
    ("ai_translation_deepseek", "AI 译文（DeepSeek）", "AI translation (DeepSeek)"),
    ("bilingual", "双语", "Bilingual"),
    ("translation_only", "纯译文", "Translation only"),
    ("side_by_side", "左右", "Side by side"),
    ("stacked", "上下", "Stacked"),
    ("retranslate", "重新翻译", "Re-translate"),
    ("unknown_time", "未知时间", "Unknown date"),
    ("close", "关闭", "Close"),
    ("saved_to_pictures", "已保存到 图片/RssRadar", "Saved to Pictures/RssRadar"),
    ("save_failed", "保存失败：图片下载或写入失败", "Save failed: image download or write failed"),
    ("share_failed", "分享失败：图片下载或写入失败", "Share failed: image download or write failed"),
    ("share_image", "分享图片", "Share image"),
    ("save_image", "保存图片", "Save image"),
    ("details", "详情", "Details"),
    ("fetch_invalid_url", "链接无效", "Invalid URL"),
    ("fetch_timeout", "请求超时", "Request timed out"),
    ("fetch_network", "网络连接失败", "Network connection failed"),
    ("fetch_http_401", "需要登录（401）", "Login required (401)"),
    ("fetch_http_403", "被站点拒绝（403）", "Rejected by the site (403)"),
    ("fetch_http_404", "页面不存在（404）", "Page not found (404)"),
    ("fetch_http_429", "请求太频繁（429）", "Too many requests (429)"),
    ("fetch_http_5xx", "站点服务器错误（5xx）", "Site server error (5xx)"),
    ("fetch_http_other", "站点返回异常状态", "Unexpected status from the site"),
    ("fetch_empty_body", "返回内容为空", "Response body was empty"),
    ("fetch_decode_error", "内容解码失败", "Failed to decode content"),
    ("fetch_extract_failed", "正文提取失败", "Body extraction failed"),
    ("issue_too_short", "正文过短", "Body too short"),
    ("issue_no_paragraph", "没有段落结构", "No paragraph structure"),
    ("issue_dynamic_render", "页面由脚本动态渲染", "Page rendered dynamically by scripts"),
    ("issue_paywall", "疑似付费墙或登录墙", "Likely a paywall or login wall"),
    ("issue_metadata_missing", "缺少标题或时间", "Missing title or date"),
]

# (file, 旧精确串, 新精确串, 期望次数)
REPL = [
    ("AiArticleSheet.kt", '"AI 分析"', "stringResource(R.string.ai_analysis)", 1),
    ("AiArticleSheet.kt", '"按需生成，生成后保存在本地，刷新不会覆盖。"', "stringResource(R.string.ai_on_demand_hint)", 1),
    ("AiArticleSheet.kt", '"还没有生成任何分析，点上面的按钮试试"', "stringResource(R.string.ai_empty)", 1),
    ("AiArticleSheet.kt", '"${feature.label} · 未开启"', "stringResource(R.string.ai_feature_disabled, feature.label)", 1),
    ("AiArticleSheet.kt", '"未配置 API Key"', "stringResource(R.string.ai_key_missing)", 1),
    ("AiArticleSheet.kt", '"AI 功能需要你自己的 DeepSeek Key。到「我的 → AI 与诊断」里填入后即可使用。"', "stringResource(R.string.ai_key_needed_hint)", 1),
    ("AiArticleSheet.kt", '"问这篇文章…"', "stringResource(R.string.ai_ask_hint)", 1),
    ("AiArticleSheet.kt", '"思考中"', "stringResource(R.string.ai_thinking)", 1),
    ("AiArticleSheet.kt", '"提问"', "stringResource(R.string.ai_ask)", 1),
    ("AiArticleSheet.kt", '"也可以输入一个术语，解释它在本文中的含义"', "stringResource(R.string.ai_term_hint)", 1),
    ("AiArticleSheet.kt", '"查证中"', "stringResource(R.string.ai_verifying)", 1),
    ("AiArticleSheet.kt", '"解释术语"', "stringResource(R.string.ai_explain_term)", 1),
    ("AiArticleSheet.kt", '"${payload.topic}  ·  置信度 ${(payload.confidence * 100).toInt()}%"', "stringResource(R.string.ai_topic_confidence, payload.topic, (payload.confidence * 100).toInt())", 1),
    ("AiArticleSheet.kt", '"也可能是：${payload.alternatives.joinToString(" / ")}"', 'stringResource(R.string.ai_alternatives, payload.alternatives.joinToString(" / "))', 1),
    ("AiArticleSheet.kt", '"偏正面"', "stringResource(R.string.ai_sentiment_positive)", 1),
    ("AiArticleSheet.kt", '"偏负面"', "stringResource(R.string.ai_sentiment_negative)", 1),
    ("AiArticleSheet.kt", '"中性"', "stringResource(R.string.ai_sentiment_neutral)", 1),
    ("AiArticleSheet.kt", '"$label  ·  强度 ${(payload.score * 100).toInt()}%"', "stringResource(R.string.ai_sentiment_score, label, (payload.score * 100).toInt())", 1),
    ("AiArticleSheet.kt", '"综合 ${payload.overall} / 100"', "stringResource(R.string.ai_overall, payload.overall)", 1),
    ("AiArticleSheet.kt", '"信息密度"', "stringResource(R.string.ai_info_density)", 1),
    ("AiArticleSheet.kt", '"原创性"', "stringResource(R.string.ai_originality)", 1),
    ("AiArticleSheet.kt", '"证据充分性"', "stringResource(R.string.ai_evidence)", 1),
    ("AiArticleSheet.kt", '"标题党程度"', "stringResource(R.string.ai_clickbait)", 1),
    ("AiArticleSheet.kt", '"信息价值 ${payload.value} / 100"', "stringResource(R.string.ai_value, payload.value)", 1),
    ("AiArticleSheet.kt", '"噪声信号"', "stringResource(R.string.ai_noise)", 1),
    ("AiArticleSheet.kt", '"实质要点"', "stringResource(R.string.ai_key_points)", 1),
    ("AiArticleSheet.kt", '"事实"', "stringResource(R.string.ai_fact)", 1),
    ("AiArticleSheet.kt", '"数据"', "stringResource(R.string.ai_data)", 1),
    ("AiArticleSheet.kt", '"观点"', "stringResource(R.string.ai_opinion)", 1),
    ("AiArticleSheet.kt", '"依据：${claim.basis}"', "stringResource(R.string.ai_claim_basis, claim.basis)", 1),
    ("AiArticleSheet.kt", '"信号较强"', "stringResource(R.string.ai_signal_strong)", 1),
    ("AiArticleSheet.kt", '"信号一般"', "stringResource(R.string.ai_signal_medium)", 1),
    ("AiArticleSheet.kt", '"信号较弱"', "stringResource(R.string.ai_signal_weak)", 1),
    ("AiArticleSheet.kt", '"信息不足"', "stringResource(R.string.ai_signal_insufficient)", 1),
    ("AiArticleSheet.kt", '"存疑点：${payload.doubts.joinToString("；")}"', 'stringResource(R.string.ai_doubts, payload.doubts.joinToString("；"))', 1),
    ("AiArticleSheet.kt", '"长推"', "stringResource(R.string.ai_style_long)", 1),
    ("AiArticleSheet.kt", '"要点体"', "stringResource(R.string.ai_style_bullets)", 1),
    ("AiArticleSheet.kt", '"短评"', "stringResource(R.string.ai_style_short)", 1),
    ("AiArticleSheet.kt", '"依据"', "stringResource(R.string.ai_basis)", 1),
    ("AiArticleSheet.kt", '"文中未提及"', "stringResource(R.string.ai_not_in_article)", 1),
    ("AiArticleSheet.kt", '"未能从该页面提取到正文"', "stringResource(R.string.ai_no_content)", 1),
    ("AiArticleSheet.kt", '"已提取 ${payload.html.length} 字并写入正文，向上滚动即可阅读"', "stringResource(R.string.ai_fulltext_done, payload.html.length)", 1),
    ("ArticleDetailScreen.kt", '"文章不存在"', "stringResource(R.string.article_not_found)", 1),
    ("ArticleDetailScreen.kt", '"返回"', "stringResource(R.string.nav_back)", 1),
    ("ArticleDetailScreen.kt", '"生成 AI 摘要"', "stringResource(R.string.ai_gen_summary)", 1),
    ("ArticleDetailScreen.kt", '"切回原文"', "stringResource(R.string.ai_back_to_original)", 1),
    ("ArticleDetailScreen.kt", '"AI 翻译"', "stringResource(R.string.ai_translate)", 1),
    ("ArticleDetailScreen.kt", '"更多操作"', "stringResource(R.string.more_actions)", 1),
    ("ArticleDetailScreen.kt", '"分享"', "stringResource(R.string.share)", 1),
    ("ArticleDetailScreen.kt", '"排版设置"', "stringResource(R.string.typography_settings)", 2),
    ("ArticleDetailScreen.kt", '"本文"', "stringResource(R.string.body_section)", 1),
    ("ArticleDetailScreen.kt", '"正文"', "stringResource(R.string.body_full)", 1),
    ("ArticleDetailScreen.kt", '"摘要"', "stringResource(R.string.body_summary)", 1),
    ("ArticleDetailScreen.kt", '"摘要 = 订阅源自带的简介。仅对本文生效，换一篇自动恢复正文。"', "stringResource(R.string.summary_hint)", 1),
    ("ArticleDetailScreen.kt", '"阅读主题"', "stringResource(R.string.reading_theme)", 1),
    ("ArticleDetailScreen.kt", '"只换背景与文字，强调色仍用应用配色；选定后固定，不随系统深浅变化。"', "stringResource(R.string.reading_theme_hint)", 1),
    ("ArticleDetailScreen.kt", '"正文渲染器"', "stringResource(R.string.body_renderer)", 1),
    ("ArticleDetailScreen.kt", '"字号"', "stringResource(R.string.font_size)", 1),
    ("ArticleDetailScreen.kt", '"减小字号"', "stringResource(R.string.font_decrease)", 1),
    ("ArticleDetailScreen.kt", '"增大字号"', "stringResource(R.string.font_increase)", 1),
    ("ArticleDetailScreen.kt", '"行距"', "stringResource(R.string.line_height)", 1),
    ("ArticleDetailScreen.kt", '"边距"', "stringResource(R.string.margin)", 1),
    ("ArticleDetailScreen.kt", '"字间距"', "stringResource(R.string.letter_spacing)", 1),
    ("ArticleDetailScreen.kt", '"正文对齐"', "stringResource(R.string.text_align)", 1),
    ("ArticleDetailScreen.kt", '"图片"', "stringResource(R.string.images)", 1),
    ("ArticleDetailScreen.kt", '"圆角"', "stringResource(R.string.corner_radius)", 1),
    ("ArticleDetailScreen.kt", '"点击放大"', "stringResource(R.string.tap_to_zoom)", 1),
    ("ArticleDetailScreen.kt", '"沉浸模式"', "stringResource(R.string.immersive_mode)", 1),
    ("ArticleDetailScreen.kt", '"隐藏分享按钮、推荐阅读、评论区等杂乱内容"', "stringResource(R.string.immersive_hint)", 1),
    ("ArticleDetailScreen.kt", '"滚动时自动隐藏工具栏"', "stringResource(R.string.auto_hide_bars)", 1),
    ("ArticleDetailScreen.kt", '"下滚收起顶栏与底栏，上滚或回到顶部时重新出现"', "stringResource(R.string.auto_hide_hint)", 1),
    ("ArticleDetailScreen.kt", '"下拉 / 上拉切换上下篇"', "stringResource(R.string.pull_switch)", 1),
    ("ArticleDetailScreen.kt", '"顶部下拉看上一篇，底部上拉看下一篇；只在整页滚动模式生效"', "stringResource(R.string.pull_switch_hint)", 1),
    ("ArticleDetailScreen.kt", '"上一篇"', "stringResource(R.string.prev_article)", 1),
    ("ArticleDetailScreen.kt", '"下一篇"', "stringResource(R.string.next_article)", 1),
    ("ArticleDetailScreen.kt", '"收藏"', "stringResource(R.string.star)", 1),
    ("ArticleDetailScreen.kt", '"稍后读"', "stringResource(R.string.read_later)", 1),
    ("ArticleDetailScreen.kt", '"AI 分析"', "stringResource(R.string.ai_analysis)", 1),
    ("ArticleDetailScreen.kt", '"查看原文"', "stringResource(R.string.view_original)", 1),
    ("ArticleDetailScreen.kt", '"相关阅读"', "stringResource(R.string.related_reads)", 1),
    ("ReadingBody.kt", '"阅读约 $minutes 分钟"', "stringResource(R.string.article_reading_time, minutes)", 1),
    ("ReadingBody.kt", '"重试"', "stringResource(R.string.retry)", 2),
    ("ReadingBody.kt", '"当前显示订阅源摘要，不是全文"', "stringResource(R.string.summary_banner)", 1),
    ("ReadingBody.kt", '"看正文"', "stringResource(R.string.see_full)", 1),
    ("ReadingBody.kt", '"本文没有可显示的正文，可查看原文。"', "stringResource(R.string.body_empty)", 1),
    ("ReadingBody.kt", '"正在获取全文…"', "stringResource(R.string.fetching_full)", 1),
    ("ReadingBody.kt", '"AI 摘要"', "stringResource(R.string.ai_summary)", 1),
    ("ReadingBody.kt", '"正在生成摘要…"', "stringResource(R.string.generating_summary)", 1),
    ("ReadingBody.kt", '"重新生成"', "stringResource(R.string.regenerate)", 1),
    ("ReadingBody.kt", '"收起"', "stringResource(R.string.collapse)", 1),
    ("ReadingBody.kt", '"展开全文"', "stringResource(R.string.expand_full)", 1),
    ("ReadingBody.kt", '"生成摘要"', "stringResource(R.string.ai_gen_summary)", 1),
    ("ReadingBody.kt", '"翻译中 ${(state as TranslationState.Progressing).doneCount}/${state.total} 段"', "stringResource(R.string.article_translating_progress, (state as TranslationState.Progressing).doneCount, state.total)", 1),
    ("ReadingBody.kt", '"AI 译文（DeepSeek）"', "stringResource(R.string.ai_translation_deepseek)", 1),
    ("ReadingBody.kt", '"双语"', "stringResource(R.string.bilingual)", 1),
    ("ReadingBody.kt", '"纯译文"', "stringResource(R.string.translation_only)", 1),
    ("ReadingBody.kt", '"左右"', "stringResource(R.string.side_by_side)", 1),
    ("ReadingBody.kt", '"上下"', "stringResource(R.string.stacked)", 1),
    ("ReadingBody.kt", '"重新翻译"', "stringResource(R.string.retranslate)", 1),
    ("ReadingBody.kt", '"切回原文"', "stringResource(R.string.ai_back_to_original)", 1),
    ("ReadingBody.kt", '"未知时间"', "stringResource(R.string.unknown_time)", 1),
    ("ReaderImagePage.kt", '"关闭"', "stringResource(R.string.close)", 1),
    ("ReaderImagePage.kt", 'if (uri != null) "已保存到 图片/RssRadar" else "保存失败：图片下载或写入失败",', 'if (uri != null) context.getString(R.string.saved_to_pictures) else context.getString(R.string.save_failed),', 1),
    ("ReaderImagePage.kt", 'Toast.makeText(context, "分享失败：图片下载或写入失败", Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()', 1),
    ("ReaderImagePage.kt", '"分享图片"', "stringResource(R.string.share_image)", 2),
    ("ReaderImagePage.kt", '"保存图片"', "stringResource(R.string.save_image)", 1),
    ("ArticleNativeReader.kt", 'AnnotatedString("详情")', "AnnotatedString(stringResource(R.string.details))", 1),
    ("ArticleWebView.kt", '"查看原文"', "stringResource(R.string.view_original)", 1),
]

BANNER_OLD = """@Composable
private fun FetchFailedBanner(
    reason: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {"""
BANNER_NEW = """@Composable
private fun FetchFailedBanner(
    reason: FetchFailReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (reason) {
        is FetchFailReason.FromFailure ->
            stringResource(R.string.article_fetch_failed, stringResource(reason.failure.uiRes()))
        FetchFailReason.FeedDisabled -> stringResource(R.string.article_fetch_feed_disabled)
        FetchFailReason.ArticleMissing -> stringResource(R.string.article_not_found)
        is FetchFailReason.ShorterThanExisting ->
            stringResource(R.string.article_fetch_shorter, reason.got, reason.existing)
    }"""
BANNER_TEXT_OLD = """                text = "没能取到正文：$reason","""
BANNER_TEXT_NEW = """                text = message,"""
INCOMPLETE_OLD = """                text = "正文可能不完整（${issue?.label ?: "站点限制或动态加载"}），可查看原文","""
INCOMPLETE_NEW = """                text = stringResource(
                    R.string.article_incomplete,
                    issue?.uiRes()?.let { stringResource(it) } ?: stringResource(R.string.issue_site_limit),
                ),"""

UI_RES_HELPERS = """
/** ADR-0017：枚举只给身份，人话由 UI 层按当前语言翻译。 */
@Composable
private fun FetchFailure.uiRes(): Int = when (this) {
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

@Composable
private fun ExtractionIssue.uiRes(): Int = when (this) {
    ExtractionIssue.NONE -> R.string.issue_site_limit
    ExtractionIssue.TOO_SHORT -> R.string.issue_too_short
    ExtractionIssue.NO_PARAGRAPH -> R.string.issue_no_paragraph
    ExtractionIssue.DYNAMIC_RENDER -> R.string.issue_dynamic_render
    ExtractionIssue.PAYWALL -> R.string.issue_paywall
    ExtractionIssue.METADATA_MISSING -> R.string.issue_metadata_missing
}
"""

VM_FAILED_OLD = "    data class Failed(val reason: String) : ContentFetchState"
VM_FAILED_NEW = "    data class Failed(val reason: FetchFailReason) : ContentFetchState"
VM_SEALED = """
/** 抓取失败的结构化原因：VM 不产文案，翻译在 UI 层（ADR-0017 §3）。 */
sealed interface FetchFailReason {
    data class FromFailure(val failure: FetchFailure) : FetchFailReason
    data object FeedDisabled : FetchFailReason
    data object ArticleMissing : FetchFailReason
    data class ShorterThanExisting(val got: Int, val existing: Int) : FetchFailReason
}
"""

# ReadingBody / ArticleDetailViewModel 需要 import 的符号（缺失时补）
NEED_IMPORTS = {
    "ReadingBody.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
        "import com.cycling.rssradar.core.data.parser.FetchFailure",
        "import com.cycling.rssradar.core.data.parser.ExtractionIssue",
    ],
    "AiArticleSheet.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
    ],
    "ArticleDetailScreen.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
    ],
    "ReaderImagePage.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
    ],
    "ArticleNativeReader.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
    ],
    "ArticleWebView.kt": [
        "import com.cycling.rssradar.R",
        "import androidx.compose.ui.res.stringResource",
    ],
}


def apply(path: pathlib.Path, old: str, new: str, expect: int = 1) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text and new in text:
        return  # 已应用
    n = text.count(old)
    if n != expect:
        sys.exit(f"[miss] {path.name}: {old[:60]!r} 命中 {n} 次（期望 {expect}）")
    path.write_text(text.replace(old, new), encoding="utf-8")


def ensure_imports(path: pathlib.Path, imports: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for imp in imports:
        if f"\n{imp}\n" in text or text.startswith(imp + "\n"):
            continue
        m = re.search(r"(^package .*$)", text, re.M)
        text = text[: m.end()] + "\n\n" + imp + text[m.end():]
    path.write_text(text, encoding="utf-8")


def main() -> None:
    counts: dict[str, int] = {}
    for fname, old, new, expect in REPL:
        path = DIR / fname
        text = path.read_text(encoding="utf-8")
        n = text.count(old)
        if n == 0 and new in text:
            continue  # 已应用（重跑幂等）
        if n != expect:
            sys.exit(f"[miss] {fname}: {old[:60]!r} 命中 {n} 次（期望 {expect}）")
        counts[fname] = counts.get(fname, 0) + expect
        path.write_text(text.replace(old, new), encoding="utf-8")
    for k, v in sorted(counts.items()):
        print(f"  {k}: {v} 处")
    print(f"字面量替换 {len(REPL)} 组")

    for fname, imports in NEED_IMPORTS.items():
        ensure_imports(DIR / fname, imports)

    rb = DIR / "ReadingBody.kt"
    apply(rb, BANNER_OLD, BANNER_NEW)
    apply(rb, BANNER_TEXT_OLD, BANNER_TEXT_NEW)
    apply(rb, INCOMPLETE_OLD, INCOMPLETE_NEW)
    rb.write_text(rb.read_text(encoding="utf-8").rstrip() + "\n" + UI_RES_HELPERS, encoding="utf-8")

    vm = DIR / "ArticleDetailViewModel.kt"
    apply(vm, VM_FAILED_OLD, VM_FAILED_NEW)
    apply(vm, 'ContentFetchState.Failed("该订阅源关闭了正文抓取，只显示订阅源自带内容")', "ContentFetchState.Failed(FetchFailReason.FeedDisabled)")
    apply(vm, 'ContentFetchState.Failed("文章不存在或已被清理")', "ContentFetchState.Failed(FetchFailReason.ArticleMissing)")
    apply(vm, 'ContentFetchState.Failed("抓到的正文比现有内容还短（$got 字 < $existing 字），未覆盖")', "ContentFetchState.Failed(FetchFailReason.ShorterThanExisting(got, existing))")
    vm_text = vm.read_text(encoding="utf-8")
    vm_text = vm_text.replace(
        "is OnDemandResult.Failed -> ContentFetchState.Failed(kind.label)",
        "is OnDemandResult.Failed -> ContentFetchState.Failed(FetchFailReason.FromFailure(kind))",
    )
    vm.write_text(vm_text.rstrip() + "\n" + VM_SEALED, encoding="utf-8")

    for locale, idx in (("values", 0), ("values-en", 1)):
        xml = ROOT / f"app/src/main/res/{locale}/strings.xml"
        text = xml.read_text(encoding="utf-8")
        if "i18n batch 2" in text:
            continue  # 已应用
        block = "\n    <!-- i18n batch 2: reading domain (ADR-0017) -->\n" + "".join(
            f'    <string name="{k}">{vals[idx]}</string>\n' for k, *vals in STRINGS
        )
        text = text.replace("</resources>", block + "</resources>")
        xml.write_text(text, encoding="utf-8")
    print(f"strings 条目 +{len(STRINGS)} (zh/en)")


if __name__ == "__main__":
    main()
