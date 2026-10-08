package com.cycling.rssradar.ui.ai

import androidx.annotation.StringRes
import com.cycling.rssradar.core.model.AiCategory
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.model.AiScope
import com.cycling.rssradar.core.model.AiTrigger

/**
 * AI 枚举文案的资源映射：core 层的中文 label/summary 等是数据口径，
 * 界面展示一律走这里按当前语言取 res。新增功能必须同步补齐四条翻译。
 */
fun AiFeature.labelRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_label
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_label
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_label
        AiFeature.QA -> R.string.ai_feature_qa_label
        AiFeature.NOISE -> R.string.ai_feature_noise_label
        AiFeature.OUTLINE -> R.string.ai_feature_outline_label
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_label
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_label
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_label
        AiFeature.RELATED -> R.string.ai_feature_related_label
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_label
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_label
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_label
        AiFeature.USAGE -> R.string.ai_feature_usage_label
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_label
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_label
}

fun AiFeature.summaryRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_summary
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_summary
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_summary
        AiFeature.QA -> R.string.ai_feature_qa_summary
        AiFeature.NOISE -> R.string.ai_feature_noise_summary
        AiFeature.OUTLINE -> R.string.ai_feature_outline_summary
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_summary
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_summary
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_summary
        AiFeature.RELATED -> R.string.ai_feature_related_summary
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_summary
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_summary
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_summary
        AiFeature.USAGE -> R.string.ai_feature_usage_summary
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_summary
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_summary
}

fun AiFeature.entryRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_entry
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_entry
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_entry
        AiFeature.QA -> R.string.ai_feature_qa_entry
        AiFeature.NOISE -> R.string.ai_feature_noise_entry
        AiFeature.OUTLINE -> R.string.ai_feature_outline_entry
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_entry
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_entry
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_entry
        AiFeature.RELATED -> R.string.ai_feature_related_entry
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_entry
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_entry
        AiFeature.FILTER_RULE -> R.string.ai_feature_filter_rule_entry
        AiFeature.USAGE -> R.string.ai_feature_usage_entry
        AiFeature.TASK_QUEUE -> R.string.ai_feature_task_queue_entry
        AiFeature.PROMPT_TEMPLATE -> R.string.ai_feature_prompt_template_entry
}

fun AiFeature.presentationRes(): Int = when (this) {
        AiFeature.SUMMARY -> R.string.ai_feature_summary_presentation
        AiFeature.TRANSLATE -> R.string.ai_feature_translate_presentation
        AiFeature.KEYWORDS -> R.string.ai_feature_keywords_presentation
        AiFeature.QA -> R.string.ai_feature_qa_presentation
        AiFeature.NOISE -> R.string.ai_feature_noise_presentation
        AiFeature.OUTLINE -> R.string.ai_feature_outline_presentation
        AiFeature.GLOSSARY -> R.string.ai_feature_glossary_presentation
        AiFeature.PERSONAL_FEED -> R.string.ai_feature_personal_feed_presentation
        AiFeature.FEED_RECOMMEND -> R.string.ai_feature_feed_recommend_presentation
        AiFeature.RELATED -> R.string.ai_feature_related_presentation
        AiFeature.SHARE_COPY -> R.string.ai_feature_share_copy_presentation
        AiFeature.FEED_HEALTH -> R.string.ai_feature_feed_health_presentation
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
