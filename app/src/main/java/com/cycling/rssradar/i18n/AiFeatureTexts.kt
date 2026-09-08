package com.cycling.rssradar.i18n

import androidx.annotation.StringRes
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.ai.AiCategory
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiScope
import com.cycling.rssradar.core.data.ai.AiTrigger

/**
 * AI 枚举文案的资源映射（ADR-0017）：core 层的中文 label/summary 等是数据口径，
 * 界面展示一律走这里按当前语言取 res。新增功能必须同步补齐四条翻译。
 */
fun AiFeature.labelRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_presentation
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_presentation
        AiFeature.CLASSIFY -> R.string.ai_feature_classify_presentation
        AiFeature.TAGS -> R.string.ai_feature_tags_presentation
        AiFeature.SENTIMENT -> R.string.ai_feature_sentiment_presentation
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_presentation
        AiFeature.OPINION -> R.string.ai_feature_opinion_presentation
        AiFeature.QA -> R.string.ai_feature_qa_presentation
        AiFeature.FULLTEXT -> R.string.ai_feature_fulltext_presentation
        AiFeature.DEDUPE -> R.string.ai_feature_dedupe_presentation
        AiFeature.QUALITY -> R.string.ai_feature_quality_presentation
        AiFeature.NOISE -> R.string.ai_feature_noise_presentation
        AiFeature.OUTLINE -> R.string.ai_feature_outline_presentation
        AiFeature.CREDIBILITY -> R.string.ai_feature_credibility_presentation
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_presentation
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_presentation
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_presentation
        AiFeature.DISCOVER -> R.string.ai_feature_discover_presentation
        AiFeature.TOPIC_GALAXY -> R.string.ai_feature_topic_galaxy_presentation
        AiFeature.BUBBLE_BREAK -> R.string.ai_feature_bubble_break_presentation
        AiFeature.RELATED -> R.string.ai_feature_related_presentation
        AiFeature.AGGREGATE -> R.string.ai_feature_aggregate_presentation
        AiFeature.INTEREST_RANK -> R.string.ai_feature_interest_rank_presentation
        AiFeature.EVENT_MERGE -> R.string.ai_feature_event_merge_presentation
        AiFeature.COLD_START -> R.string.ai_feature_cold_start_presentation
        AiFeature.DAILY_BRIEF -> R.string.ai_feature_daily_brief_presentation
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_presentation
        AiFeature.SMART_NOTIFY -> R.string.ai_feature_smart_notify_presentation
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_presentation
        AiFeature.HABIT -> R.string.ai_feature_habit_presentation
        AiFeature.DAILY_REPORT -> R.string.ai_feature_daily_report_presentation
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_presentation
        AiFeature.USAGE -> R.string.ai_feature_usage_presentation
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_presentation
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_presentation
}

fun AiFeature.summaryRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_summary
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_summary
        AiFeature.CLASSIFY -> R.string.ai_feature_classify_summary
        AiFeature.TAGS -> R.string.ai_feature_tags_summary
        AiFeature.SENTIMENT -> R.string.ai_feature_sentiment_summary
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_summary
        AiFeature.OPINION -> R.string.ai_feature_opinion_summary
        AiFeature.QA -> R.string.ai_feature_qa_summary
        AiFeature.FULLTEXT -> R.string.ai_feature_fulltext_summary
        AiFeature.DEDUPE -> R.string.ai_feature_dedupe_summary
        AiFeature.QUALITY -> R.string.ai_feature_quality_summary
        AiFeature.NOISE -> R.string.ai_feature_noise_summary
        AiFeature.OUTLINE -> R.string.ai_feature_outline_summary
        AiFeature.CREDIBILITY -> R.string.ai_feature_credibility_summary
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_summary
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_summary
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_summary
        AiFeature.DISCOVER -> R.string.ai_feature_discover_summary
        AiFeature.TOPIC_GALAXY -> R.string.ai_feature_topic_galaxy_summary
        AiFeature.BUBBLE_BREAK -> R.string.ai_feature_bubble_break_summary
        AiFeature.RELATED -> R.string.ai_feature_related_summary
        AiFeature.AGGREGATE -> R.string.ai_feature_aggregate_summary
        AiFeature.INTEREST_RANK -> R.string.ai_feature_interest_rank_summary
        AiFeature.EVENT_MERGE -> R.string.ai_feature_event_merge_summary
        AiFeature.COLD_START -> R.string.ai_feature_cold_start_summary
        AiFeature.DAILY_BRIEF -> R.string.ai_feature_daily_brief_summary
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_summary
        AiFeature.SMART_NOTIFY -> R.string.ai_feature_smart_notify_summary
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_summary
        AiFeature.HABIT -> R.string.ai_feature_habit_summary
        AiFeature.DAILY_REPORT -> R.string.ai_feature_daily_report_summary
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_summary
        AiFeature.USAGE -> R.string.ai_feature_usage_summary
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_summary
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_summary
}

fun AiFeature.entryRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_entry
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_entry
        AiFeature.CLASSIFY -> R.string.ai_feature_classify_entry
        AiFeature.TAGS -> R.string.ai_feature_tags_entry
        AiFeature.SENTIMENT -> R.string.ai_feature_sentiment_entry
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_entry
        AiFeature.OPINION -> R.string.ai_feature_opinion_entry
        AiFeature.QA -> R.string.ai_feature_qa_entry
        AiFeature.FULLTEXT -> R.string.ai_feature_fulltext_entry
        AiFeature.DEDUPE -> R.string.ai_feature_dedupe_entry
        AiFeature.QUALITY -> R.string.ai_feature_quality_entry
        AiFeature.NOISE -> R.string.ai_feature_noise_entry
        AiFeature.OUTLINE -> R.string.ai_feature_outline_entry
        AiFeature.CREDIBILITY -> R.string.ai_feature_credibility_entry
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_entry
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_entry
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_entry
        AiFeature.DISCOVER -> R.string.ai_feature_discover_entry
        AiFeature.TOPIC_GALAXY -> R.string.ai_feature_topic_galaxy_entry
        AiFeature.BUBBLE_BREAK -> R.string.ai_feature_bubble_break_entry
        AiFeature.RELATED -> R.string.ai_feature_related_entry
        AiFeature.AGGREGATE -> R.string.ai_feature_aggregate_entry
        AiFeature.INTEREST_RANK -> R.string.ai_feature_interest_rank_entry
        AiFeature.EVENT_MERGE -> R.string.ai_feature_event_merge_entry
        AiFeature.COLD_START -> R.string.ai_feature_cold_start_entry
        AiFeature.DAILY_BRIEF -> R.string.ai_feature_daily_brief_entry
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_entry
        AiFeature.SMART_NOTIFY -> R.string.ai_feature_smart_notify_entry
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_entry
        AiFeature.HABIT -> R.string.ai_feature_habit_entry
        AiFeature.DAILY_REPORT -> R.string.ai_feature_daily_report_entry
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_entry
        AiFeature.USAGE -> R.string.ai_feature_usage_entry
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_entry
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_entry
}

fun AiFeature.presentationRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_presentation
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_presentation
        AiFeature.CLASSIFY -> R.string.ai_feature_classify_presentation
        AiFeature.TAGS -> R.string.ai_feature_tags_presentation
        AiFeature.SENTIMENT -> R.string.ai_feature_sentiment_presentation
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_presentation
        AiFeature.OPINION -> R.string.ai_feature_opinion_presentation
        AiFeature.QA -> R.string.ai_feature_qa_presentation
        AiFeature.FULLTEXT -> R.string.ai_feature_fulltext_presentation
        AiFeature.DEDUPE -> R.string.ai_feature_dedupe_presentation
        AiFeature.QUALITY -> R.string.ai_feature_quality_presentation
        AiFeature.NOISE -> R.string.ai_feature_noise_presentation
        AiFeature.OUTLINE -> R.string.ai_feature_outline_presentation
        AiFeature.CREDIBILITY -> R.string.ai_feature_credibility_presentation
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_presentation
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_presentation
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_presentation
        AiFeature.DISCOVER -> R.string.ai_feature_discover_presentation
        AiFeature.TOPIC_GALAXY -> R.string.ai_feature_topic_galaxy_presentation
        AiFeature.BUBBLE_BREAK -> R.string.ai_feature_bubble_break_presentation
        AiFeature.RELATED -> R.string.ai_feature_related_presentation
        AiFeature.AGGREGATE -> R.string.ai_feature_aggregate_presentation
        AiFeature.INTEREST_RANK -> R.string.ai_feature_interest_rank_presentation
        AiFeature.EVENT_MERGE -> R.string.ai_feature_event_merge_presentation
        AiFeature.COLD_START -> R.string.ai_feature_cold_start_presentation
        AiFeature.DAILY_BRIEF -> R.string.ai_feature_daily_brief_presentation
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_presentation
        AiFeature.SMART_NOTIFY -> R.string.ai_feature_smart_notify_presentation
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_presentation
        AiFeature.HABIT -> R.string.ai_feature_habit_presentation
        AiFeature.DAILY_REPORT -> R.string.ai_feature_daily_report_presentation
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_presentation
        AiFeature.USAGE -> R.string.ai_feature_usage_presentation
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_presentation
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_presentation
}

fun AiCategory.labelRes(): Int = when (this) {
        AiCategory.CONTENT -> R.string.ai_category_content_label
        AiCategory.DISCOVERY -> R.string.ai_category_discovery_label
        AiCategory.ASSIST -> R.string.ai_category_assist_label
}

fun AiCategory.descriptionRes(): Int = when (this) {
        AiCategory.CONTENT -> R.string.ai_category_content_description
        AiCategory.DISCOVERY -> R.string.ai_category_discovery_description
        AiCategory.ASSIST -> R.string.ai_category_assist_description
}

fun AiScope.labelRes(): Int = when (this) {
        AiScope.ARTICLE -> R.string.ai_scope_article_label
        AiScope.FEED -> R.string.ai_scope_feed_label
        AiScope.GLOBAL -> R.string.ai_scope_global_label
}

fun AiTrigger.labelRes(): Int = when (this) {
        AiTrigger.MANUAL -> R.string.ai_trigger_manual_label
        AiTrigger.ON_DEMAND -> R.string.ai_trigger_on_demand_label
        AiTrigger.BATCH -> R.string.ai_trigger_batch_label
        AiTrigger.REALTIME -> R.string.ai_trigger_realtime_label
}

fun AiTrigger.descriptionRes(): Int = when (this) {
        AiTrigger.MANUAL -> R.string.ai_trigger_manual_description
        AiTrigger.ON_DEMAND -> R.string.ai_trigger_on_demand_description
        AiTrigger.BATCH -> R.string.ai_trigger_batch_description
        AiTrigger.REALTIME -> R.string.ai_trigger_realtime_description
}
