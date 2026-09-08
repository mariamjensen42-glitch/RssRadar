# -*- coding: utf-8 -*-
"""i18n 批3：ui/me 文案资源化（ADR-0017）。

- 简单字面量：'old' -> stringResource(R.string.key)
- 特殊替换：模板串 / 非组合上下文 / 结构重构（TipsScreen、FetchDiagnostics）
- 生成中英 strings.xml（幂等），自动补 import
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = "app/src/main/java/com/cycling/rssradar/ui/me"

EN_SIMPLE = {
    "back": "Back", "refresh": "Refresh", "delete": "Delete", "clear": "Clear",
    "cancel": "Cancel", "save": "Save", "export": "Export", "close": "Close",
    "select": "Select", "enter": "Open", "current_tag": "Current", "unknown": "Unknown",
    "ai_results_title": "AI results", "tab_artifacts": "Artifacts", "tab_features": "Features",
    "filter_all": "All", "copy_raw": "Copy raw output", "open_article": "Open article",
    "open_feed": "Open feed", "collapse_raw": "Hide raw output", "view_raw": "View raw output",
    "artifact_unparseable": "This artifact has no parseable content",
    "artifacts_page_desc": "All AI feature results live here, filterable by feature. Numbers come from the local artifacts table.",
    "artifacts_empty_none": "No AI features yet",
    "artifacts_empty_feature": "No artifacts for this feature yet",
    "artifacts_empty_hint": "Enable some AI features and their results will gather here.",
    "artifacts_empty_queue_hint": "Enabled but nothing yet? Check the task queue on the AI features page.",
    "model_output": "Model output",
    "ai_features_title": "AI features", "view_results": "View results",
    "restore_defaults": "Restore defaults", "disable_all_paid": "Turn all model-calling features off (save money)",
    "usage_title": "Usage", "unlimited": "Unlimited", "calls_today": "Calls today",
    "not_used_today": "Not used today", "io_today": "Input / output today",
    "calls_total": "Total calls", "failed_total": "Total failed",
    "budget_section": "Rate limits & budget",
    "budget_hint": "Once the cap is hit, no more requests go out today; tasks stay queued for tomorrow.",
    "daily_limit": "Daily limit", "concurrency": "Concurrency", "min_interval": "Min interval",
    "queue_title": "Task queue", "status_pending": "Pending", "status_running": "Running",
    "status_done": "Done", "status_failed": "Failed", "running_now": "Running…",
    "run_now": "Run now", "retry_failed": "Retry failed", "clear_pending": "Clear pending",
    "all_off": "Turn all off", "all_on": "Turn all on", "enable": "Enable",
    "needs_llm": "Model", "local": "Local", "trigger_label": "Trigger",
    "entry_label": "How to run", "presentation_label": "Where it shows",
    "crash_title": "Crash logs", "crash_clear": "Clear logs",
    "crash_empty": "No crash records yet",
    "crash_desc": "Crashes are logged automatically with device info; up to 5 reports are kept.",
    "crash_lost": "(log missing or failed to read)",
    "crash_confirm_title": "Clear crash logs?",
    "tap_expand": "Tap to view full text",
    "crash_export_empty": "Log is empty, nothing to export",
    "crash_file_title": "RssRadar crash log", "crash_export_title": "Export crash log",
    "crash_export_failed": "Export failed",
    "diag_title": "Full-text fetch diagnostics", "diag_clear": "Clear records",
    "diag_empty": "No failed or incomplete fetch records yet",
    "diag_empty_hint": "Records appear after you open a summary-only article and trigger a full-text fetch.",
    "by_site": "By site", "unknown_site": "(unknown site)", "failure_label": "Failed",
    "issue_too_short": "Body too short", "issue_no_paragraph": "No body paragraphs found",
    "issue_js_render": "Likely JS-rendered content", "issue_paywall": "Likely paywalled or login-walled",
    "issue_missing_meta": "Missing title or date", "issue_incomplete": "Body incomplete",
    "profile_title": "Interest profile",
    "profile_empty": "No preferences learned yet",
    "profile_empty_hint": "Interest terms appear after you read a few articles; until then the Recommended tab rotates recent unread across your feeds.",
    "deleted_feed": "(deleted feed)",
    "profile_formula": "Affinity = this feed's historical open rate (recent opens weighted higher), normalized to the strongest feed.",
    "prompt_title": "Prompt templates", "prompt_add": "New override",
    "prompt_builtin_tpl": "Built-in summary template",
    "prompt_save_hint": "Save empty to switch back to the built-in template",
    "prompt_clear": "Clear override",
    "prompt_pick_feed": "Choose a feed", "prompt_search_feed": "Search feeds",
    "prompt_empty": "No feed-level prompt overrides yet. All feeds use the built-in template below; tap \"New override\" (top right) to customize a single feed.",
    "stats_title": "Reading stats",
    "stats_scope": "Last 7 days · counts real opens only; swipe-marked read is excluded",
    "stats_opened": "Opened (7d)", "stats_minutes": "Reading minutes (est.)",
    "stats_streak": "Day streak", "stats_saved": "Starred / read later",
    "stats_hours": "Active hours",
    "stats_insufficient": "Not enough data — too few opens in the last 7 days",
    "stats_peak": "Hours clearly above the daily average",
    "stats_top_feeds": "Most-opened feeds",
    "stats_no_records": "No opens recorded in the last 7 days",
    "stats_concentrated": "Reading concentrated in a few feeds",
    "stats_moderate": "Moderately spread", "stats_scattered": "Widely spread",
    "me_title": "Me", "me_feeds": "Feeds", "me_unread": "Unread",
    "last_7_days": "Last 7 days", "settings_general": "General",
    "settings_appearance": "Appearance", "theme_label": "Theme",
    "theme_system": "System", "theme_light": "Light", "theme_dark": "Dark",
    "settings_sync": "Sync & cleanup", "settings_ai": "AI & diagnostics",
    "ai_configured": "Configured", "ai_not_configured": "Not configured",
    "accent_color": "Accent color",
    "accent_desc": "Affects buttons, selection and links only; cards and backgrounds keep RssRadar's palette.",
    "hue": "Hue", "preview_aa": "Preview Aa", "saturation": "Saturation", "lightness": "Lightness",
    "dynamic_color": "Dynamic color",
    "dynamic_color_desc": "Accent follows the system wallpaper; cards and backgrounds keep RssRadar's palette.",
    "dynamic_color_unsupported": "System dynamic color requires Android 12+; not supported on this device.",
    "list_display": "List display",
    "list_display_desc": "Which items article cards show in the home and feed lists; global, applies immediately.",
    "view_mode": "Layout", "show_feed_icon": "Feed icons", "show_feed_name": "Feed names",
    "show_date": "Date", "show_thumbnail": "Thumbnails", "desc_label": "Description",
    "sticky_date_header": "Sticky date headers", "dim_read": "Dim read",
    "mark_read_on_scroll": "Mark read on scroll", "recommendation": "Recommended",
    "recommendation_desc": "Ranks unread articles by your real reading behavior; computed entirely on-device, the profile never leaves it.",
    "show_recommend_tab": "Show the \"Recommended\" tab",
    "link_share": "Links & sharing",
    "link_share_desc": "How external links open and what sharing includes. Share from the reader top bar.",
    "open_links": "Open links", "share_content": "Share content",
    "custom_tabs_hint": "In-app Custom Tabs would need the androidx.browser dependency; not available yet.",
    "about": "About",
    "about_desc": "Version comes from the installed package; the update check only reads GitHub's latest release.",
    "auto_sync": "Auto sync",
    "auto_sync_desc": "Refreshes feeds periodically in the background, then archives by retention after each sync. Manual refresh ignores these limits.",
    "sync_interval": "Sync interval", "wifi_only": "Wi-Fi only", "charging_only": "Charging only",
    "sync_on_launch": "Sync on launch", "article_cleanup": "Article cleanup",
    "cleanup_desc": "Articles older than the selected retention are cleaned up (on app open and after auto sync). Starred and read-later articles are never cleaned.",
    "keep_over": "Published over", "new_article_notify": "New-article notifications",
    "notify_desc": "Posts one summary notification when auto sync finds new articles. Per-feed switches live on each feed's action page.",
    "enable_notify": "Enable notifications",
    "rsshub_instance": "RSSHub instance",
    "instance_desc": "Route resolution goes through an RSSHub instance. The official one is unreachable on some networks — auto-probe or enter a self-hosted one.",
    "current_instance": "Current instance", "probing": "Probing…",
    "auto_probe": "Probe for a working instance", "custom_instance": "Custom instance (optional)",
    "custom_instance_hint": "A custom instance overrides the probe result; save empty to clear it.",
    "builtin_mirrors": "Built-in mirrors (tap to fill; probing picks the fastest)",
    "route_catalog": "Route catalog",
    "catalog_desc": "The full built-in RSSHub route set, searchable and filterable when adding feeds. Update online to pick up new official routes.",
    "catalog_count_label": "Routes", "loading_ellipsis": "Loading…", "data_time": "Data as of",
    "updated_suffix": "(updated)", "builtin_suffix": "(built-in)", "updating": "Updating…",
    "update_catalog": "Update route catalog",
    "ai_desc": "Article AI summary and translation are powered by DeepSeek with your own API key — you control the cost and quota.",
    "status_label": "Status", "key_reveal_hint": "Press to show the key, release to hide",
    "save_key": "Save key", "ai_features_desc": "35 AI features with individual switches: content, discovery and assist. Usage and the background task queue live here.",
    "features_and_usage": "Features & usage", "body_fetch": "Full-text fetch",
    "body_fetch_desc": "When on-demand fetch fails or comes back incomplete, see the site, status code and reason here.",
    "diagnostics": "Diagnostics",
    "crash_desc2": "Crashes are logged automatically with device info; up to 5 reports, exportable.",
    "update_current_version": "Current version", "checking": "Checking…",
    "check_updates": "Check for updates", "up_to_date": "Up to date", "go_download": "Download",
    "tips_screen_title": "Tips & troubleshooting",
    "tips_section_basics": "Good to know first", "tips_section_trouble": "If something breaks",
    "aimsg_reset_done": "Settings restored to defaults",
    "aimsg_paid_disabled": "All model-calling features are now off",
    "aimsg_no_failed": "No failed tasks",
    "aimsg_pending_cleared": "Pending tasks cleared",
    "aimsg_no_background": "No background features enabled yet — turn some on below before running.",
    "aimsg_started_background": "Started in the background — see the queue for progress.",
    "aimsg_nothing_to_process": "Nothing to process (no recent candidates, or artifacts already exist).",
    "aimsg_no_executable": "Tasks queued but none executable (today's budget may be used up).",
    "aimsg_run_error": "Run failed — try again later (details in the task queue).",
    "aimsg_delete_failed": "Delete failed, please retry",
    "aimsg_prompt_builtin": "Switched back to the built-in summary prompt",
    "aimsg_prompt_saved": "Saved this feed's summary prompt",
    "notify_permission_needed": "Please grant notification permission in the system dialog",
    "notify_permission_missing": "Notification permission missing — can't enable new-article notifications",
    "ai_key_cleared": "API key cleared", "ai_key_saved": "API key saved",
    "instance_cleared": "Custom instance cleared", "instance_invalid": "Invalid instance URL",
    "probe_none": "No built-in instance reachable — check your network or enter a self-hosted instance.",
    "catalog_update_failed_generic": "Update failed: network error",
}

SPECIAL_STR = {
    "artifacts_feature_count": ("%1$s · %2$s 条", "%1$s · %2$s items"),
    "subject_article": ("文章 #%1$s", "Article #%1$s"),
    "subject_feed": ("订阅源 #%1$s", "Feed #%1$s"),
    "subject_global": ("全局 · %1$s", "Global · %1$s"),
    "artifacts_input_chars": ("输入 %1$s 字", "In %1$s chars"),
    "artifacts_output_chars": ("输出 %1$s 字", "Out %1$s chars"),
    "chars_suffix": ("%1$s 字", "%1$s chars"),
    "calls_suffix": ("%1$s 次", "%1$s calls"),
    "io_today_chars": ("%1$s / %2$s 字", "%1$s / %2$s chars"),
    "enable_all_category": ("全部开启「%1$s」", "Enable all \"%1$s\""),
    "enable_all_warning": ("将开启 %1$s 项功能，可能产生 API 调用费用。", "This will enable %1$s features and may incur API costs."),
    "clear_pending_warning": ("将丢弃队列中 %1$s 个待执行任务，此操作不可撤销。", "This will discard %1$s pending tasks in the queue. This cannot be undone."),
    "crash_confirm_msg": ("已记录的 %1$s 份崩溃日志会被删除，无法恢复。", "The %1$s recorded crash logs will be deleted. This cannot be undone."),
    "diag_failures": ("失败 %1$s", "Failed: %1$s"),
    "diag_incomplete": ("不完整 %1$s", "Incomplete: %1$s"),
    "diag_detail_count": ("明细（%1$s 条）", "Details (%1$s)"),
    "diag_retries": ("重试 %1$s 次", "Retried %1$s time(s)"),
    "diag_pages": ("%1$s 页", "%1$s page(s)"),
    "diag_chars": ("%1$s 字", "%1$s chars"),
    "profile_desc": ("推荐流按你的真实阅读行为排序：打开过的文章、收藏、稍后读都会计入画像，越近的行为权重越高。画像只存在本机，不上传。", "The recommended feed ranks by your real reading behavior: opened articles, stars and read-later all feed the profile, with more recent actions weighted higher. The profile never leaves your device."),
    "profile_terms": ("兴趣词 %1$s 个", "%1$s interest terms"),
    "profile_top_hint": ("仅展示权重最高的 30 个，完整词袋共 %1$s 个", "Showing the top 30 only; the full vocabulary has %1$s terms."),
    "profile_affinities": ("订阅源亲和度 %1$s 个", "%1$s feed affinities"),
    "prompt_overrides_count": ("自定义覆盖 · %1$s", "Custom overrides · %1$s"),
    "prompt_variable_help": ("可用变量：%1$s；留空则使用内置摘要提示词。", "Available variables: %1$s; leave blank to use the built-in summary prompt."),
    "prompt_page_desc": ("目前支持订阅源级摘要提示词覆盖。可用变量：%1$s；留空则使用内置摘要提示词。", "Per-feed summary prompt overrides are supported. Available variables: %1$s; leave blank to use the built-in summary prompt."),
    "stats_hour": ("%1$s 点", "%1$s:00"),
    "stats_articles": ("%1$s 篇", "%1$s articles"),
    "new_version_found": ("发现新版本 %1$s", "New version found: %1$s"),
    "catalog_updated": ("已更新，共 %1$s 条路由", "Updated: %1$s routes in total"),
    "catalog_update_failed": ("更新失败：%1$s", "Update failed: %1$s"),
    "instance_saved": ("已保存：%1$s", "Saved: %1$s"),
    "probe_found": ("探测到可用实例：%1$s", "Found a working instance: %1$s"),
    "aimsg_requeued": ("已重新排队 %1$s 个任务", "Re-queued %1$s task(s)"),
    "aimsg_artifacts_cleared": ("已清除「%1$s」的全部产物", "Cleared all artifacts of \"%1$s\""),
    "aimsg_need_toggle": ("先打开「%1$s」的开关再运行", "Turn on \"%1$s\" before running it"),
    "aimsg_report_ok": ("本次执行 %1$s 项：成功 %2$s", "Processed %1$s: %2$s succeeded"),
    "aimsg_report_ok_budget": ("本次执行 %1$s 项：成功 %2$s（今日额度已用完，余下任务明天继续）", "Processed %1$s: %2$s succeeded (daily budget used up; the rest continue tomorrow)"),
    "aimsg_report_fail": ("本次执行 %1$s 项：成功 %2$s，失败 %3$s", "Processed %1$s: %2$s succeeded, %3$s failed"),
    "aimsg_report_fail_budget": ("本次执行 %1$s 项：成功 %2$s，失败 %3$s（今日额度已用完，余下任务明天继续）", "Processed %1$s: %2$s succeeded, %3$s failed (daily budget used up; the rest continue tomorrow)"),
    "set_link_browser": ("系统浏览器", "System browser"), "set_link_ask": ("每次询问", "Ask every time"),
    "set_share_title_link": ("标题 + 链接", "Title + link"), "set_share_link": ("仅链接", "Link only"),
    "set_share_title_summary_link": ("标题 + 摘要 + 链接", "Title + summary + link"),
    "set_desc_none": ("关", "Off"), "set_desc_short": ("短", "Short"), "set_desc_long": ("长", "Long"),
    "set_view_list": ("列表", "List"), "set_view_card": ("卡片", "Card"),
    "set_view_magazine": ("杂志", "Magazine"), "set_view_grid": ("网格", "Grid"),
    "set_sync_manually": ("手动", "Manual"), "set_sync_1h": ("每小时", "Hourly"),
    "set_sync_3h": ("每 3 小时", "Every 3 hours"), "set_sync_6h": ("每 6 小时", "Every 6 hours"),
    "set_sync_12h": ("每 12 小时", "Every 12 hours"), "set_sync_1d": ("每天", "Daily"),
    "set_keep_always": ("永久", "Forever"), "set_keep_1d": ("1 天", "1 day"),
    "set_keep_2d": ("2 天", "2 days"), "set_keep_3d": ("3 天", "3 days"),
    "set_keep_1w": ("1 周", "1 week"), "set_keep_2w": ("2 周", "2 weeks"),
    "set_keep_1m": ("1 个月", "1 month"),
}

TIPS = [
    ("订阅源要从 RSSHub 路由里挑",
     "RSSHub 是本站的核心数据源。设置 → RSSHub → 路由目录里能按站点挑现成路由；在别的 App 里看到链接，用系统分享给 RssRadar 也能直接开加订阅抽屉。",
     "Feeds come from RSSHub routes",
     "RSSHub is this app's core data source. Settings → RSSHub → Route catalog lists ready-made routes by site; or share a link from another app to RssRadar to open the add-subscription sheet directly."),
    ("老用户迁移：先导入 OPML",
     "订阅页顶栏菜单里有「导入 OPML」。从别的阅读器过来时这是第一步。",
     "Migrating? Import OPML first",
     "The subscriptions top-bar menu has \"Import OPML\". That's step one when coming from another reader."),
    ("自动同步可以按条件收窄",
     "设置 → 同步与清理里能设「仅 WiFi」「仅充电」「启动时同步」与同步间隔。想省电省流量就把条件开上。",
     "Auto sync can be narrowed by conditions",
     "Settings → Sync & cleanup offers Wi-Fi only, charging only, sync-on-launch and the sync interval. Turn the conditions on to save battery and data."),
    ("正文是按需抓取的",
     "列表里存的是订阅源给的内容，完整正文在打开文章时才去抓（省流量）。排版面板里能调字号、行距、字间距、阅读主题。",
     "Full text is fetched on demand",
     "The list stores what the feed provides; the full body is fetched when you open the article (saves data). The typography panel adjusts font size, line height, letter spacing and reading theme."),
    ("归档默认永久保留，清理要你自己开",
     "设置 → 同步与清理 → 归档保留档位，默认是「永久」。存量数据大，升级即删不可接受，所以自动清理必须 opt-in。收藏与稍后读不受清理影响。",
     "Archives are kept forever unless you opt in",
     "Settings → Sync & cleanup → retention defaults to \"Forever\". Existing libraries are large, so deleting on upgrade is unacceptable — auto cleanup is strictly opt-in. Starred and read-later articles are never touched."),
]
TROUBLE = [
    ("加不上订阅源",
     "常见原因是链接本身不是有效 feed、站点返回 4xx、证书错误、域名解析失败，或单份 feed 体积过大。加订阅时会告诉你探测结果；已经加上的源如果连续失败，会被标记成「失效源」，在订阅管理页的失效源筛选里能看到原因和连续失败次数——修好之后任意一次成功刷新即自动恢复。",
     "Can't add a feed",
     "Common causes: the link isn't a valid feed, the site returns 4xx, certificate errors, DNS failures, or an oversized feed. Adding a feed reports the probe result; feeds that keep failing get flagged as dead — the dead-feed filter in feed management shows the reason and consecutive failure count, and any successful refresh restores them automatically."),
    ("正文只有摘要，或者很短",
     "入库时取的是订阅源 description 与 content 的较长者，很多源给的就是摘要本身。打开文章时会再去抓一次完整正文；站点有付费墙、靠 JS 渲染或反爬时可能抓不到，阅读页会写明原因并给「重试」按钮。想按站点看整体抓取情况，去设置 → AI 与诊断 → 全文抓取诊断。",
     "Body is just a summary, or very short",
     "On ingest the longer of the feed's description and content is kept — many feeds only provide a summary. Opening the article triggers a full-text fetch; paywalls, JS rendering or anti-crawling can block it, and the reader will explain why with a Retry button. For per-site fetch health, see Settings → AI & diagnostics → Full-text fetch diagnostics."),
    ("收不到新文章通知",
     "三个地方都得开：系统的通知权限、App 内的通知总开关（默认关）、以及订阅源自己的通知开关。少任何一处都不会响。",
     "No new-article notifications",
     "Three switches must all be on: the system notification permission, the in-app master switch (off by default), and the feed's own notification switch. Miss any one and nothing rings."),
    ("AI 功能没反应",
     "AI 摘要与翻译用你自己的 DeepSeek API Key，先在设置 → AI 与诊断里配好 Key。配好之后仍无结果，多半是撞上了日预算、并发或请求间隔的限流。",
     "AI features do nothing",
     "AI summary and translation use your own DeepSeek API key — configure it in Settings → AI & diagnostics first. Still nothing? You're probably hitting the daily budget, concurrency or min-interval limits."),
    ("文章越积越多",
     "同上：归档保留档位默认永久，想要自动清理就去设一个档位（1 天 / 1 周 / 1 个月）。",
     "Articles keep piling up",
     "Same as above: retention defaults to Forever. Set a tier (1 day / 1 week / 1 month) to enable auto cleanup."),
    ("App 崩过",
     "设置 → AI 与诊断 → 崩溃日志，能看到最近几次崩溃并导出全文，反馈时直接附上。",
     "The app crashed",
     "Settings → AI & diagnostics → Crash logs shows recent crashes with exportable full text — attach it when reporting."),
]
for i, (t, b, te, be) in enumerate(TIPS, 1):
    SPECIAL_STR[f"tip{i}_title"] = (t, te)
    SPECIAL_STR[f"tip{i}_body"] = (b, be)
for i, (t, b, te, be) in enumerate(TROUBLE, 1):
    SPECIAL_STR[f"ts{i}_title"] = (t, te)
    SPECIAL_STR[f"ts{i}_body"] = (b, be)

FILES = {
    "AiArtifactsScreen.kt": {
        "simple": {
            "返回": "back", "AI 结果": "ai_results_title", "刷新": "refresh",
            "还没有任何 AI 产物": "artifacts_empty_none", "这项功能还没有产物": "artifacts_empty_feature",
            "开启几项 AI 功能后，跑出来的结果会集中显示在这里": "artifacts_empty_hint",
            "功能已开启但还没跑出结果？去「AI 智能功能」页的任务队列看看": "artifacts_empty_queue_hint",
            "产物": "tab_artifacts", "功能": "tab_features", "模型输出": "model_output",
            "全部 AI 功能的生成结果都在这里，按功能筛选后查看。数字来自本地产物表。": "artifacts_page_desc",
            "全部": "filter_all", "这条产物没有可解析的内容": "artifact_unparseable",
            "收起模型原文": "collapse_raw", "查看模型原文": "view_raw", "复制原文": "copy_raw",
            "打开文章": "open_article", "打开订阅源": "open_feed", "删除": "delete",
        },
        "special": [
            ('text = "${group.feature.label} ${group.total}",',
             'text = "${stringResource(group.feature.labelRes())} ${group.total}",'),
            ('text = "${feature.label} · ${list.size} 条",',
             'text = stringResource(R.string.artifacts_feature_count, stringResource(feature.labelRes()), list.size),'),
            ('text = item.feature.label,', 'text = stringResource(item.feature.labelRes()),', 2),
            ('"文章 #${item.subjectId}"', 'stringResource(R.string.subject_article, item.subjectId)'),
            ('"订阅源 #${item.subjectId}"', 'stringResource(R.string.subject_feed, item.subjectId)'),
            ('"全局 · ${item.subjectId}"', 'stringResource(R.string.subject_global, item.subjectId)'),
            ('"输入 ${formatCount(item.inputChars.toLong())} 字"',
             'stringResource(R.string.artifacts_input_chars, formatCount(item.inputChars.toLong()))'),
            ('"输出 ${formatCount(item.outputChars.toLong())} 字"',
             'stringResource(R.string.artifacts_output_chars, formatCount(item.outputChars.toLong()))'),
            ('"${formatCount(outputChars)} 字"', 'stringResource(R.string.chars_suffix, formatCount(outputChars))'),
        ],
        "extra_imports": ["com.cycling.rssradar.i18n.labelRes"],
    },
    "AiFeaturesScreen.kt": {
        "simple": {
            "返回": "back", "AI 智能功能": "ai_features_title", "查看结果": "view_results",
            "恢复默认": "restore_defaults", "全部关闭（省钱）": "disable_all_paid",
            "用量": "usage_title", "不限": "unlimited", "今日调用": "calls_today",
            "今日未使用": "not_used_today", "今日输入 / 输出": "io_today",
            "累计调用": "calls_total", "累计失败": "failed_total", "限流与预算": "budget_section",
            "上限到顶后当天不再发起请求，任务留在队列里等第二天。": "budget_hint",
            "每日上限": "daily_limit", "并发数": "concurrency", "请求间隔": "min_interval",
            "任务队列": "queue_title", "待执行": "status_pending", "进行中": "status_running",
            "已完成": "status_done", "失败": "status_failed", "执行中…": "running_now",
            "立即执行": "run_now", "立即运行": "run_now", "重试失败": "retry_failed",
            "清空待执行": "clear_pending", "清空": "clear", "取消": "cancel",
            "全部关闭": "all_off", "全部开启": "all_on", "开启": "enable",
            "调用模型": "needs_llm", "本地": "local", "触发方式": "trigger_label",
            "交互入口": "entry_label", "结果展示": "presentation_label",
        },
        "special": [
            ('text = "${category.label}  $enabledCount/${all.size}",',
             'text = "${stringResource(category.labelRes())}  $enabledCount/${all.size}",'),
            ('text = category.description,', 'text = stringResource(category.descriptionRes()),'),
            ('Text("全部开启「${category.label}」"',
             'Text(stringResource(R.string.enable_all_category, stringResource(category.labelRes()))'),
            ('text = feature.label,', 'text = stringResource(feature.labelRes()),'),
            ('text = feature.summary,', 'text = stringResource(feature.summaryRes()),'),
            ('TagChip(text = feature.trigger.label, tint = colors.accent)',
             'TagChip(text = stringResource(feature.trigger.labelRes()), tint = colors.accent)'),
            ('DetailLine(stringResource(R.string.trigger_label), feature.trigger.description)',
             'DetailLine(stringResource(R.string.trigger_label), stringResource(feature.trigger.descriptionRes()))'),
            ('DetailLine(stringResource(R.string.entry_label), feature.entry)',
             'DetailLine(stringResource(R.string.entry_label), stringResource(feature.entryRes()))'),
            ('DetailLine(stringResource(R.string.presentation_label), feature.presentation)',
             'DetailLine(stringResource(R.string.presentation_label), stringResource(feature.presentationRes()))'),
            ('"${formatCount(budget.inputCharsToday)} / ${formatCount(budget.outputCharsToday)} 字"',
             'stringResource(R.string.io_today_chars, formatCount(budget.inputCharsToday), formatCount(budget.outputCharsToday))'),
            ('"${formatCount(budget.totalCalls)} 次"', 'stringResource(R.string.calls_suffix, formatCount(budget.totalCalls))'),
            ('"${formatCount(budget.totalFailed)} 次"', 'stringResource(R.string.calls_suffix, formatCount(budget.totalFailed))'),
            ('"将开启 ${all.size} 项功能，可能产生 API 调用费用。"',
             'stringResource(R.string.enable_all_warning, all.size)'),
            ('"将丢弃队列中 ${queue.pending} 个待执行任务，此操作不可撤销。"',
             'stringResource(R.string.clear_pending_warning, queue.pending)'),
        ],
        "extra_imports": ["com.cycling.rssradar.i18n.labelRes", "com.cycling.rssradar.i18n.summaryRes",
                          "com.cycling.rssradar.i18n.entryRes", "com.cycling.rssradar.i18n.presentationRes",
                          "com.cycling.rssradar.i18n.descriptionRes"],
    },
    "CrashLogScreen.kt": {
        "simple": {
            "返回": "back", "崩溃日志": "crash_title", "清空日志": "crash_clear",
            "暂无崩溃记录": "crash_empty",
            "应用崩溃时会自动记录异常与设备信息，最多保留 5 份": "crash_desc",
            "（日志已丢失或读取失败）": "crash_lost", "导出": "export", "关闭": "close",
            "清空崩溃日志？": "crash_confirm_title", "清空": "clear", "取消": "cancel",
            "点击查看全文": "tap_expand", "日志为空，无法导出": "crash_export_empty",
            "RssRadar 崩溃日志": "crash_file_title", "导出崩溃日志": "crash_export_title",
            "无法导出": "crash_export_failed",
        },
        "special": [
            ('"已记录的 ${records.size} 份崩溃日志会被删除，无法恢复。"',
             'stringResource(R.string.crash_confirm_msg, records.size)'),
        ],
        "extra_imports": [],
    },
    "InterestProfileScreen.kt": {
        "simple": {
            "返回": "back", "兴趣画像": "profile_title", "还没有学到偏好": "profile_empty",
            "多读几篇文章后这里会出现兴趣词；在此之前，推荐 tab 按订阅源轮转展示最近未读。": "profile_empty_hint",
            "（已删除的订阅源）": "deleted_feed",
            "亲和度 = 该源文章的历史打开率（越近的打开权重越高），按最高的那个源归一化。": "profile_formula",
        },
        "special": [
            ('"推荐流按你的真实阅读行为排序：打开过的文章、收藏、稍后读都会计入画像，" +\n                    "越近的行为权重越高。画像只存在本机，不上传。"',
             'stringResource(R.string.profile_desc)'),
            ('"兴趣词 ${state.terms.size} 个"', 'stringResource(R.string.profile_terms, state.terms.size)'),
            ('"仅展示权重最高的 30 个，完整词袋共 ${state.terms.size} 个"',
             'stringResource(R.string.profile_top_hint, state.terms.size)'),
            ('"订阅源亲和度 ${state.affinities.size} 个"',
             'stringResource(R.string.profile_affinities, state.affinities.size)'),
        ],
        "extra_imports": [],
    },
    "PromptTemplatesScreen.kt": {
        "simple": {
            "返回": "back", "提示词模板": "prompt_title", "新增覆盖": "prompt_add",
            "内置摘要模板": "prompt_builtin_tpl", "留空保存即改用内置模板": "prompt_save_hint",
            "清除覆盖": "prompt_clear", "保存": "save", "选择订阅源": "prompt_pick_feed",
            "搜索订阅源": "prompt_search_feed", "已删除的源": "deleted_feed",
            "还没有订阅源自定义提示词。所有订阅源目前使用下方内置模板；点右上角「新增覆盖」为单个源定制。": "prompt_empty",
        },
        "special": [
            ('"自定义覆盖 · ${state.overrides.size}"', 'stringResource(R.string.prompt_overrides_count, state.overrides.size)'),
            ('text = "目前支持订阅源级摘要提示词覆盖。${AiPrompts.summaryVariableHelp()}",',
             'text = stringResource(R.string.prompt_page_desc, AiPrompts.summaryVariables().joinToString("，")),'),
            ('text = AiPrompts.summaryVariableHelp(),',
             'text = stringResource(R.string.prompt_variable_help, AiPrompts.summaryVariables().joinToString("，")),'),
        ],
        "extra_imports": [],
    },
    "ReadingStatsScreen.kt": {
        "simple": {
            "返回": "back", "阅读统计": "stats_title",
            "近 7 天 · 口径为真实打开文章，滑动标已读不计入": "stats_scope",
            "近 7 天打开": "stats_opened", "阅读分钟（估算）": "stats_minutes",
            "连续阅读天数": "stats_streak", "收藏/稍后读": "stats_saved",
            "活跃时段": "stats_hours",
            "样本不足——近 7 天打开太少，看不出习惯": "stats_insufficient",
            "明显高于全天平均的打开时段": "stats_peak", "最常打开的订阅源": "stats_top_feeds",
            "近 7 天还没有打开记录": "stats_no_records", "阅读集中在少数源": "stats_concentrated",
            "分布适中": "stats_moderate", "阅读相当分散": "stats_scattered",
        },
        "special": [
            ('"$it 点"', 'stringResource(R.string.stats_hour, it)'),
            ('"${feed.cnt} 篇"', 'stringResource(R.string.stats_articles, feed.cnt)'),
        ],
        "extra_imports": [],
    },
    "RssHubSettingsScreen.kt": {
        "simple": {
            "跟随系统": "theme_system", "浅色": "theme_light", "深色": "theme_dark",
            "我的": "me_title", "订阅源": "me_feeds", "未读文章": "me_unread",
            "阅读统计": "stats_title", "近 7 天": "last_7_days", "通用": "settings_general",
            "同步与清理": "settings_sync", "AI 与诊断": "settings_ai",
            "已配置": "ai_configured", "未配置": "ai_not_configured", "进入": "enter",
        },
        "special": [
            ('summary = if (state.aiKeyConfigured) "已配置" else "未配置",',
             'summary = if (state.aiKeyConfigured) stringResource(R.string.ai_configured) else stringResource(R.string.ai_not_configured),'),
            ('summary = state.sync.interval.label,', 'summary = stringResource(state.sync.interval.labelRes()),'),
        ],
        "extra_imports": ["com.cycling.rssradar.i18n.labelRes"],
    },
    "UpdateCheckRow.kt": {
        "simple": {
            "当前版本": "update_current_version", "未知": "unknown", "检查中…": "checking",
            "检查更新": "check_updates", "已是最新版本": "up_to_date", "去下载": "go_download",
        },
        "special": [
            ('"发现新版本 ${current.version}"', 'stringResource(R.string.new_version_found, current.version)'),
        ],
        "extra_imports": [],
    },
}


FILES["SettingsSubPages.kt"] = {
    "simple": {
        "返回": "back", "强调色": "accent_color",
        "只影响按钮、选中态与链接；卡片和背景保持 RssRadar 原配色。": "accent_desc",
        "色相": "hue", "预览 Aa": "preview_aa", "饱和度": "saturation", "明度": "lightness",
        "选择": "select", "进入": "enter", "通用": "settings_general", "外观": "settings_appearance",
        "主题": "theme_label", "跟随系统": "theme_system", "浅色": "theme_light", "深色": "theme_dark",
        "动态取色": "dynamic_color",
        "强调色跟随系统壁纸，卡片与背景仍用 RssRadar 原配色。": "dynamic_color_desc",
        "系统动态取色需要 Android 12 及以上，当前设备不支持。": "dynamic_color_unsupported",
        "列表显示": "list_display",
        "信息流与订阅源文章列表卡片的显示项，全局生效，即改即见。": "list_display_desc",
        "视图模式": "view_mode", "订阅源图标": "show_feed_icon", "订阅源名称": "show_feed_name",
        "日期": "show_date", "缩略图": "show_thumbnail", "描述": "desc_label",
        "粘性日期头": "sticky_date_header", "已读弱化": "dim_read",
        "滚动时自动标记已读": "mark_read_on_scroll", "推荐": "recommendation",
        "按你的真实阅读行为给未读文章排序，全部计算在本机完成，画像不上传。": "recommendation_desc",
        "显示「推荐」标签页": "show_recommend_tab", "兴趣画像": "profile_title",
        "链接与分享": "link_share",
        "外链怎么打开、分享文章时带哪些内容。阅读页顶栏可分享本文。": "link_share_desc",
        "打开链接": "open_links", "分享内容": "share_content",
        "Custom Tabs（应用内打开）需引入 androidx.browser 依赖，暂未提供。": "custom_tabs_hint",
        "关于": "about", "版本号来自已安装包；检查更新只读取 GitHub 的 latest release。": "about_desc",
        "同步与清理": "settings_sync", "自动同步": "auto_sync",
        "后台周期刷新订阅源，同步完成后按保留天数清理归档。手动刷新不受这些限制。": "auto_sync_desc",
        "同步间隔": "sync_interval", "仅 WiFi": "wifi_only", "仅充电": "charging_only",
        "启动时同步": "sync_on_launch", "文章清理": "article_cleanup",
        "发布时间超过所选期限的文章会被清理（打开应用时和自动同步完成后执行）；收藏与稍后读永不清理。": "cleanup_desc",
        "发布超过": "keep_over", "新文章通知": "new_article_notify",
        "自动同步后发现新文章时发一条汇总通知。逐个订阅源可在订阅操作页单独关闭。": "notify_desc",
        "开启通知": "enable_notify", "RSSHub 实例": "rsshub_instance",
        "路由解析由 RSSHub 实例完成。官方实例在部分网络环境不可达，可自动探测或填入自建实例。": "instance_desc",
        "当前实例": "current_instance", "探测中…": "probing", "自动探测可用实例": "auto_probe",
        "自定义实例（可选）": "custom_instance",
        "自定义实例优先于探测结果；留空并保存则清除。": "custom_instance_hint",
        "保存": "save", "内置镜像（点选填入；自动探测取响应最快者）": "builtin_mirrors",
        "当前": "current_tag", "路由目录": "route_catalog",
        "内置 RSSHub 全量路由，加订阅时可搜索与分类筛选。官方新增路由后联网更新目录即可同步。": "catalog_desc",
        "收录路由": "catalog_count_label", "装载中…": "loading_ellipsis", "数据时间": "data_time",
        "（已更新）": "updated_suffix", "（内置）": "builtin_suffix", "更新中…": "updating",
        "更新路由目录": "update_catalog", "AI 与诊断": "settings_ai",
        "详情页的 AI 摘要与翻译由 DeepSeek 提供，使用你自己的 API Key，费用与额度由你掌控。": "ai_desc",
        "状态": "status_label", "按住显示 Key，松手隐藏": "key_reveal_hint",
        "保存 Key": "save_key", "AI 智能功能": "ai_features_title",
        "35 项 AI 功能各自独立开关：内容处理、推荐发现、辅助推送。用量与后台任务队列在这里看。": "ai_features_desc",
        "功能开关与用量": "features_and_usage", "AI 生成结果": "ai_results_title",
        "提示词模板": "prompt_title", "正文抓取": "body_fetch",
        "按需抓原文失败或抓不全时，这里能看到站点、状态码与原因。": "body_fetch_desc",
        "全文抓取诊断": "diag_title", "诊断": "diagnostics",
        "应用崩溃时自动记录异常与设备信息，最多保留 5 份，可导出。": "crash_desc2",
        "崩溃日志": "crash_title",
    },
    "special": [
        ('''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedChips(
                            options = ListViewMode.entries.toList(),
                            selected = display.viewMode,
                            label = { it.label },''',
         '''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val viewModeLabels = ListViewMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = ListViewMode.entries.toList(),
                            selected = display.viewMode,
                            label = { viewModeLabels.getValue(it) },'''),
        ('''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedChips(
                            options = com.cycling.rssradar.core.data.store.ListDescMode.entries.toList(),
                            selected = display.descMode,
                            label = { it.label },''',
         '''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val descModeLabels = com.cycling.rssradar.core.data.store.ListDescMode.entries.associateWith { stringResource(it.labelRes()) }
                        SegmentedChips(
                            options = com.cycling.rssradar.core.data.store.ListDescMode.entries.toList(),
                            selected = display.descMode,
                            label = { descModeLabels.getValue(it) },'''),
        ('''    if (showLinkModeSheet) {
        OptionPickerSheet(''',
         '''    if (showLinkModeSheet) {
        val linkModeLabels = LinkOpenMode.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet('''),
        ('''            options = LinkOpenMode.entries.toList(),
            selected = state.linkShare.linkOpenMode,
            label = { it.label },''',
         '''            options = LinkOpenMode.entries.toList(),
            selected = state.linkShare.linkOpenMode,
            label = { linkModeLabels.getValue(it) },'''),
        ('''    if (showShareFormatSheet) {
        OptionPickerSheet(''',
         '''    if (showShareFormatSheet) {
        val shareFormatLabels = ShareContentFormat.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet('''),
        ('''            options = ShareContentFormat.entries.toList(),
            selected = state.linkShare.shareFormat,
            label = { it.label },''',
         '''            options = ShareContentFormat.entries.toList(),
            selected = state.linkShare.shareFormat,
            label = { shareFormatLabels.getValue(it) },'''),
        ('''    if (showKeepSheet) {
        OptionPickerSheet(''',
         '''    if (showKeepSheet) {
        val keepLabels = KeepArchived.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet('''),
        ('''            options = KeepArchived.entries.toList(),
            selected = state.keepArchived,
            label = { it.label },''',
         '''            options = KeepArchived.entries.toList(),
            selected = state.keepArchived,
            label = { keepLabels.getValue(it) },'''),
        ('''    if (showIntervalSheet) {
        OptionPickerSheet(''',
         '''    if (showIntervalSheet) {
        val intervalLabels = SyncInterval.entries.associateWith { stringResource(it.labelRes()) }
        OptionPickerSheet('''),
        ('''            options = SyncInterval.entries.toList(),
            selected = state.sync.interval,
            label = { it.label },''',
         '''            options = SyncInterval.entries.toList(),
            selected = state.sync.interval,
            label = { intervalLabels.getValue(it) },'''),
        ('value = state.linkShare.linkOpenMode.label,', 'value = stringResource(state.linkShare.linkOpenMode.labelRes()),'),
        ('value = state.linkShare.shareFormat.label,', 'value = stringResource(state.linkShare.shareFormat.labelRes()),'),
    ],
    "extra_imports": ["com.cycling.rssradar.i18n.labelRes"],
}

FILES["FetchDiagnosticsScreen.kt"] = {
    "simple": {
        "返回": "back", "全文抓取诊断": "diag_title", "清空记录": "diag_clear",
        "暂无失败或不完整的抓取记录": "diag_empty",
        "打开一篇摘要型文章并触发全文抓取后，这里会出现记录": "diag_empty_hint",
        "按站点": "by_site", "（未知站点）": "unknown_site",
        "正文过短": "issue_too_short", "未找到正文段落": "issue_no_paragraph",
        "疑似 JS 动态渲染": "issue_js_render", "疑似付费墙/登录墙": "issue_paywall",
        "缺标题或时间": "issue_missing_meta", "正文不完整": "issue_incomplete",
    },
    "special": [
        ('"失败 ${stat.failures}"', 'stringResource(R.string.diag_failures, stat.failures)'),
        ('"不完整 ${stat.incomplete}"', 'stringResource(R.string.diag_incomplete, stat.incomplete)'),
        ('"明细（${problems.size} 条）"', 'stringResource(R.string.diag_detail_count, problems.size)'),
    ],
    "extra_imports": [],
}

# ── 结构重构 ────────────────────────────────────────────────────────────────

def refactor_tips(path):
    text = path.read_text(encoding="utf-8")
    if "R.string.tip1_title" in text:
        print("tips: 已应用")
        return
    start = text.index("/** 先知道这几件事：不是故障，是设计如此。 */")
    end = text.index("@Composable\nfun TipsScreen")
    tips_items = ",\n".join(f'    HelpItem(R.string.tip{i}_title, R.string.tip{i}_body)' for i in range(1, 6))
    ts_items = ",\n".join(f'    HelpItem(R.string.ts{i}_title, R.string.ts{i}_body)' for i in range(1, 7))
    new_block = f'''/** 条目只带资源 id；翻译在 UI 层按当前语言取（ADR-0017）。 */
private data class HelpItem(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)

/** 先知道这几件事：不是故障，是设计如此。 */
private val TIPS = listOf(
{tips_items},
)

/** 出问题先看这里：症状 → 原因 → 去哪处理。 */
private val TROUBLESHOOTING = listOf(
{ts_items},
)

'''
    text = text[:start] + new_block + text[end:]
    text = text.replace("text = item.title,", "text = stringResource(item.titleRes()),")
    text = text.replace("text = item.body,", "text = stringResource(item.bodyRes()),")
    text = text.replace('SettingsSubPage(title = "使用提示与疑难解答", onBack = onBack)',
                        'SettingsSubPage(title = stringResource(R.string.tips_screen_title), onBack = onBack)')
    text = text.replace('SectionHeader("先知道这几件事")', 'SectionHeader(stringResource(R.string.tips_section_basics))')
    text = text.replace('SectionHeader("出问题先看这里")', 'SectionHeader(stringResource(R.string.tips_section_trouble))')
    if "import androidx.annotation.StringRes" not in text:
        text = text.replace("import androidx.compose.foundation", "import androidx.annotation.StringRes\nimport androidx.compose.foundation", 1)
    path.write_text(text, encoding="utf-8")
    print("tips: ok")

def refactor_fetch_describe(path):
    text = path.read_text(encoding="utf-8")
    if "UiText.res(R.string.issue_too_short)" in text:
        print("fetch describe: 已应用")
        return
    old = '''/** 原因 → 中文文案；未知枚举值（老版本写入的）如实显示原始值，不编造。 */
private fun describe(log: ContentFetchLogEntity): Pair<String, String> {
    if (!log.ok) {
        val failure = runCatching { FetchFailure.valueOf(log.failure.orEmpty()) }.getOrNull()
        return (failure?.label ?: (log.failure ?: "失败")) to facts(log)
    }
    val issue = runCatching { ExtractionIssue.valueOf(log.issue.orEmpty()) }.getOrNull()
    val label = when (issue) {
        ExtractionIssue.TOO_SHORT -> "正文过短"
        ExtractionIssue.NO_PARAGRAPH -> "未找到正文段落"
        ExtractionIssue.DYNAMIC_RENDER -> "疑似 JS 动态渲染"
        ExtractionIssue.PAYWALL -> "疑似付费墙/登录墙"
        ExtractionIssue.METADATA_MISSING -> "缺标题或时间"
        else -> "正文不完整"
    }
    return label to facts(log)
}

private fun facts(log: ContentFetchLogEntity): String = buildString {
    append("重试 ${log.attempts} 次")
    log.statusCode?.let { append(" · HTTP $it") }
    if (log.pages > 1) append(" · ${log.pages} 页")
    append(" · ${log.contentChars} 字")
    append(" · ${log.durationMs} ms")
}'''
    new = '''/** 原因 → UiText；未知枚举值（老版本写入的）如实显示原始值，不编造（ADR-0017 §3）。 */
private fun describe(log: ContentFetchLogEntity, context: Context): Pair<String, String> {
    if (!log.ok) {
        val failure = runCatching { FetchFailure.valueOf(log.failure.orEmpty()) }.getOrNull()
        val label = failure?.uiRes()?.let { UiText.res(it) }
            ?: (log.failure?.let { UiText.Raw(it) } ?: UiText.res(R.string.failure_label))
        return label.resolve(context) to facts(log, context)
    }
    val issue = runCatching { ExtractionIssue.valueOf(log.issue.orEmpty()) }.getOrNull()
    val label = when (issue) {
        ExtractionIssue.TOO_SHORT -> UiText.res(R.string.issue_too_short)
        ExtractionIssue.NO_PARAGRAPH -> UiText.res(R.string.issue_no_paragraph)
        ExtractionIssue.DYNAMIC_RENDER -> UiText.res(R.string.issue_js_render)
        ExtractionIssue.PAYWALL -> UiText.res(R.string.issue_paywall)
        ExtractionIssue.METADATA_MISSING -> UiText.res(R.string.issue_missing_meta)
        else -> UiText.res(R.string.issue_incomplete)
    }
    return label.resolve(context) to facts(log, context)
}

private fun facts(log: ContentFetchLogEntity, context: Context): String {
    val parts = mutableListOf(UiText.res(R.string.diag_retries, "${log.attempts}"))
    log.statusCode?.let { parts += UiText.Raw("HTTP $it") }
    if (log.pages > 1) parts += UiText.res(R.string.diag_pages, "${log.pages}")
    parts += UiText.res(R.string.diag_chars, "${log.contentChars}")
    parts += UiText.Raw("${log.durationMs} ms")
    return parts.joinToString(" · ") { it.resolve(context) }
}'''
    assert old in text, "describe 块不匹配"
    text = text.replace(old, new)
    text = text.replace("= describe(log)", "= describe(log, context)")
    if "import com.cycling.rssradar.i18n.UiText" not in text:
        if "import com.cycling.rssradar.i18n" in text:
            text = text.replace("import com.cycling.rssradar.i18n", "import com.cycling.rssradar.i18n.UiText\nimport com.cycling.rssradar.i18n", 1)
        else:
            text = text.replace("import com.cycling.rssradar.R", "import com.cycling.rssradar.R\nimport com.cycling.rssradar.i18n.UiText", 1)
    path.write_text(text, encoding="utf-8")
    print("fetch describe: ok")

def add_summary_variables():
    p = ROOT / "core/data/src/main/kotlin/com/cycling/rssradar/core/data/ai/AiPrompts.kt"
    t = p.read_text(encoding="utf-8")
    if "fun summaryVariables()" in t:
        print("AiPrompts: 已有 summaryVariables")
        return
    old = '''    /** 订阅源级摘要提示词支持的变量说明，设置页渲染给用户看。 */
    fun summaryVariableHelp(): String =
        "可用变量：${SUMMARY_VARIABLES.joinToString("、")}；留空则使用内置摘要提示词。"'''
    new = '''    /** 订阅源级摘要提示词支持的变量说明，设置页渲染给用户看。 */
    fun summaryVariableHelp(): String =
        "可用变量：${SUMMARY_VARIABLES.joinToString("、")}；留空则使用内置摘要提示词。"

    /** 变量名列表（i18n：包装文案由 UI 层按当前语言渲染，变量本身不可翻译）。 */
    fun summaryVariables(): List<String> = SUMMARY_VARIABLES'''
    assert old in t, "AiPrompts 块不匹配"
    p.write_text(t.replace(old, new), encoding="utf-8")
    print("AiPrompts: ok")

# ── 执行 ────────────────────────────────────────────────────────────────────

def xml_escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")

def main():
    str_table = {}
    zh_from_files = {}
    for spec in FILES.values():
        for lit, key in spec["simple"].items():
            zh_from_files[key] = lit
    for key, en in EN_SIMPLE.items():
        str_table[key] = (zh_from_files.get(key), en)
    for key, (zh, en) in SPECIAL_STR.items():
        str_table[key] = (zh, en)
    for key, zh in {
        "failure_label": "失败",
        "tips_screen_title": "使用提示与疑难解答",
        "tips_section_basics": "先知道这几件事",
        "tips_section_trouble": "出问题先看这里",
        "aimsg_reset_done": "已恢复默认设置",
        "aimsg_paid_disabled": "已关闭全部会调用模型的功能",
        "aimsg_no_failed": "没有失败任务",
        "aimsg_pending_cleared": "已清空待执行任务",
        "aimsg_no_background": "还没有开启任何后台功能，先在下面打开几项再执行",
        "aimsg_started_background": "已在后台开始执行，进度见队列",
        "aimsg_nothing_to_process": "没有需要处理的内容（近期没有候选，或已有产物）",
        "aimsg_no_executable": "任务已入队但没有可执行的（今日额度可能已用完）",
        "aimsg_run_error": "执行失败，请稍后重试（失败详情见任务队列）",
        "aimsg_delete_failed": "删除失败，请重试",
        "aimsg_prompt_builtin": "已改用内置摘要提示词",
        "aimsg_prompt_saved": "已保存该订阅源的摘要提示词",
        "notify_permission_needed": "请在系统弹窗中允许通知权限",
        "notify_permission_missing": "没有通知权限，无法开启新文章通知",
        "ai_key_cleared": "已清除 API Key",
        "ai_key_saved": "API Key 已保存",
        "instance_cleared": "已清除自定义实例",
        "instance_invalid": "实例地址格式不正确",
        "probe_none": "所有内置实例均不可达，请检查网络或填入自建实例",
        "catalog_update_failed_generic": "更新失败：网络错误",
    }.items():
        if str_table.get(key, (None,))[0] is None:
            str_table[key] = (zh, str_table[key][1])

    for name, spec in FILES.items():
        path = ROOT / APP / name
        text = path.read_text(encoding="utf-8")
        for lit, key in spec["simple"].items():
            old = f'"{lit}"'
            n = text.count(old)
            if n == 0 and f'R.string.{key}' in text:
                continue
            if n == 0:
                sys.exit(f"[miss] {name}: {lit!r}")
            zh = str_table[key][0]
            if zh is None:
                str_table[key] = (lit, str_table[key][1])
            text = text.replace(old, f'stringResource(R.string.{key})')
        for spec_item in spec["special"]:
            old, new = spec_item[0], spec_item[1]
            expect = spec_item[2] if len(spec_item) > 2 else 1
            n = text.count(old)
            if n == 0 and new in text:
                continue
            if n != expect:
                sys.exit(f"[miss] {name}: {old[:60]!r} 命中 {n}（期望 {expect}）")
            text = text.replace(old, new)
        if "import com.cycling.rssradar.R\n" not in text:
            if "import androidx.compose.ui.res.stringResource" not in text:
                text = text.replace("import androidx.compose.foundation",
                                    "import androidx.compose.ui.res.stringResource\nimport com.cycling.rssradar.R\nimport androidx.compose.foundation", 1)
            else:
                text = text.replace("import androidx.compose.ui.res.stringResource\n",
                                    "import androidx.compose.ui.res.stringResource\nimport com.cycling.rssradar.R\n", 1)
        if "import androidx.compose.ui.res.stringResource" not in text:
            text = text.replace("import com.cycling.rssradar.R\n",
                                "import androidx.compose.ui.res.stringResource\nimport com.cycling.rssradar.R\n", 1)
        for imp in spec["extra_imports"]:
            if f"import {imp}\n" not in text:
                anchor = "import com.cycling.rssradar.R\n"
                text = text.replace(anchor, anchor + f"import {imp}\n", 1)
        path.write_text(text, encoding="utf-8")
        print(f"{name}: ok")

    refactor_tips(ROOT / APP / "TipsScreen.kt")
    refactor_fetch_describe(ROOT / APP / "FetchDiagnosticsScreen.kt")
    add_summary_variables()

    for locale, idx in (("values", 0), ("values-en", 1)):
        xml = ROOT / f"app/src/main/res/{locale}/strings.xml"
        text = xml.read_text(encoding="utf-8")
        if "i18n batch 3" in text:
            print(f"{locale}: 已应用")
            continue
        missing = [k for k, (zh, _) in str_table.items() if zh is None]
        if missing:
            sys.exit(f"[str] 缺中文: {missing}")
        lines = ["    <!-- i18n batch 3 -->"]
        for key, (zh, en) in str_table.items():
            lines.append(f'    <string name="{key}">{xml_escape((zh, en)[idx])}</string>')
        text = text.replace("</resources>", "\n".join(lines) + "\n</resources>")
        xml.write_text(text, encoding="utf-8")
        print(f"{locale}: +{len(str_table)}")

if __name__ == "__main__":
    main()
