# -*- coding: utf-8 -*-
"""i18n 批3 前置：AiFeature 文案资源化（ADR-0017）。

从 core/data 的 AiFeature.kt 解析 35 项功能的 label/summary/entry/presentation
与 AiCategory/AiScope/AiTrigger 的中文文案，翻译成英文，生成：
1. i18n/AiFeatureTexts.kt —— enum -> R.string 映射（app 层，core 不碰资源）
2. 中英 strings.xml 追加（幂等）
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "core/data/src/main/kotlin/com/cycling/rssradar/core/data/ai/AiFeature.kt"

# 35 项功能的英文翻译：NAME -> (label, summary, entry, presentation)
EN = {
    "SUMMARY": ("AI Summary", "Generates a conclusion plus 2-4 key points from the article body. Per-feed summary prompts are supported.", "Reading top bar Sparkles button / summary card in the body; per-feed prompt on the feed action page.", "A persistent summary card under the title in the reader; refresh never overwrites it."),
    "TRANSLATE": ("Article Translation", "Translates the full foreign-language body into Simplified Chinese, preserving HTML structure; switch back to the original anytime.", "Translation toggle in the reading top bar; re-translate from the bottom action bar.", "Replacement-style translation with an in-session LRU cache; not persisted."),
    "CLASSIFY": ("Smart Classification", "Decides the article's topic (tech / finance / dev / design, etc.) for the topic galaxy and information aggregation.", "Batch: daily job scans new unclassified articles; manual re-classify in the reader.", "A topic chip under the summary card; the topic galaxy page aggregates by it."),
    "TAGS": ("Auto Tags", "Extracts 3-6 fine-grained topic tags - more specific than classification and cross-topic.", "Batch: daily job; \"Regenerate tags\" under the summary card in the reader.", "A row of tag chips in the reader; long-press to copy to clipboard."),
    "SENTIMENT": ("Sentiment Analysis", "Detects reporting tone (positive / neutral / negative) and intensity to spot emotionally driven coverage.", "Batch: daily job; manual analysis in the reader.", "A small sentiment color bar on list cards + a sentiment label (with intensity) in the reader."),
    "KEYWORDS": ("Keyword Extraction", "Extracts 5-8 core keywords (proper nouns first), shared with the interest-profile vocabulary.", "Batch: daily job; the keyword row under the summary card in the reader.", "A keyword chip row in the reader; tap to jump into search with that term."),
    "OPINION": ("Opinion Summary", "Distills the core claims and supporting evidence, separating \"the author's view\" from \"cited facts\".", "The \"Opinion\" button on the reader's bottom action bar.", "A bottom sheet: claim list plus evidence digest, collapsible."),
    "QA": ("Article Q&A & Deep Analysis", "Ask questions about the current article; the model answers only from the body and says \"not mentioned in the article\" when unsupported.", "The \"Ask about this article\" box at the reader bottom, with one-tap follow-ups (explain / impact / timeline).", "A bottom Q&A drawer: multi-turn with per-answer \"source paragraph\" citations; not persisted."),
    "FULLTEXT": ("AI Body Restoration", "When the rule-based extractor fails, the model restores the body from raw page HTML. Off by default and not a routine body source.", "Not wired: the reader offers no entry (rule extraction + fetch diagnostics already cover body fetching).", "On success it replaces the body and clears the incomplete flag; failures are reported honestly, no dirty data."),
    "DEDUPE": ("Smart Deduplication", "Detects multi-source reposts of the same event, naming the primary article and its near-duplicates.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; article-level artifacts link to the original; a dedicated list is pending."),
    "QUALITY": ("Article Quality Analysis", "Scores information density, originality, clickbait level and evidence sufficiency, each 0-100.", "Batch: daily job; the \"Quality\" button re-scores a single article.", "A quality card in the reader: total score + four-dimension bars + one-line weak spot."),
    "NOISE": ("Noise Reduction & Content Scoring", "Flags ads, filler, AI-generated bulk content and emotional bait, with a noise suggestion and an information-value score.", "Batch: daily job; the \"Denoise\" button in the reader.", "A denoise corner badge on list cards (grayed when noisy); a denoise explainer card in the reader."),
    "OUTLINE": ("Long-read Structuring", "Breaks a long article into a hierarchical outline with sub-headings you can jump to, plus a one-line gist.", "The \"Outline\" button at the reader bottom; proactively suggested above a length threshold.", "A bottom outline drawer: hierarchical list, tap to scroll to the paragraph."),
    "CREDIBILITY": ("Source Credibility Assessment", "Evaluates source type, evidence chain and assertion strength into a credibility tier plus signals to watch.", "The \"Credibility\" button at the reader bottom.", "A credibility card: tier label + evidence list + \"doubts\"; never fabricates fact-check conclusions."),
    "GLOSSARY": ("Selection Explainer", "Select a term, abbreviation or proper noun in the body and get a one-line, context-aware explanation.", "The \"Explain\" menu item after long-press text selection in the reader.", "A bottom strip: one-line meaning in this article's context; not persisted."),
    "PERSONAL_FEED": ("Personalized Feed", "The recommendation feed's backbone: scores by freshness, source affinity and content similarity, then interleaves (ADR-0013).", "The \"Recommended\" tab in the home feed; the tab can be turned off in settings.", "The Recommended tab's article stream, with \"show less like this\" feedback and undo."),
    "FEED_RECOMMEND": ("Smart Feed Recommendations", "Suggests RSSHub routes or sites worth adding, based on your interest profile and current subscriptions.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a dedicated \"you may like\" entry is pending."),
    "DISCOVER": ("Discovery Mode", "Serves a batch of cross-domain articles on unfamiliar topics - built for exploration, not narrowing.", "A dedicated \"Discover\" entry is not yet implemented; pending once enabled.", "A dedicated exploration stream is not yet implemented; artifacts appear under \"AI results\"."),
    "TOPIC_GALAXY": ("Topic Galaxy Browser", "Clusters classified articles into topic constellations laid out by heat and affinity - a map of your reading.", "A dedicated \"Topic galaxy\" entry is not yet implemented; depends on classification artifacts.", "A dedicated galaxy page is not yet implemented; artifacts appear under \"AI results\"."),
    "BUBBLE_BREAK": ("Filter-Bubble Breaker", "Finds blind-spot topics your profile misses and pushes a few opposing or unfamiliar perspectives.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a \"another angle\" card in the feed is pending."),
    "RELATED": ("Related Articles", "While reading, surfaces related articles across feeds, reusing the recommendation engine's content similarity.", "The \"Related reading\" strip at the reader bottom; purely local similarity - no model, no quota.", "A horizontal card strip at the reader bottom: title + source, tap to open; hidden when empty."),
    "AGGREGATE": ("Smart Information Aggregation", "Synthesizes multiple articles on one topic into a briefing that preserves disagreement instead of flattening it.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a dedicated briefing page is pending."),
    "INTEREST_RANK": ("Personal Interest Ranking", "Has the model produce readable names and merges for the profile vocabulary, ranked into \"what you care about most\".", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; interest-profile integration is pending."),
    "EVENT_MERGE": ("Same-Event Timeline", "Merges coverage of the same event into one timeline, showing how it evolved and how sources differ.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a dedicated timeline page is pending."),
    "COLD_START": ("Interest Cold Start", "New users tick a few interest areas to bootstrap a profile, skipping the \"empty recommendation\" phase.", "A dedicated onboarding card is not yet implemented.", "A dedicated picker page is not yet implemented; artifacts appear under \"AI results\"."),
    "DAILY_BRIEF": ("AI Daily Briefing", "Compresses the day's new articles into a briefing: top stories, what matters to you, and a skippable list.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a dedicated briefing page and notification entry are pending."),
    "SHARE_COPY": ("Share Copywriter", "Generates platform-appropriate share copy for an article (short take / long post / bullet style).", "Inside the reader top bar's share button: \"AI copy\".", "A copy picker sheet: three style cards; tap to copy and open the system share sheet."),
    "SMART_NOTIFY": ("Smart Notifications", "Scores new articles for importance first - only the ones worth interrupting you are pushed; the rest stay silent.", "Batch: judged after each sync; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; notification-shade integration is pending."),
    "FEED_HEALTH": ("Feed Health Monitoring", "Combines fetch logs and update cadence to diagnose dead, slowing or degrading feeds.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature (feed-level artifacts link to the feed); a dedicated report page is pending."),
    "HABIT": ("Reading Habit Analysis", "Analyzes active hours, reading-time distribution and feed concentration, calling out lopsided intake.", "Daily job; the \"Reading habits\" entry on the Me page.", "A habit report page: hour heatmap + source concentration + three actionable observations."),
    "DAILY_REPORT": ("Daily Reading Report", "Summarizes what you read, how long it took and what you missed, with one honest takeaway.", "Batch: daily job; \"Run now\" from the expanded AI features page.", "View under \"AI results\" filtered by feature; a dedicated report page and next-day notification are pending."),
    "FILTER_RULE": ("Smart Filter Rules", "Describe \"what I don't want to see\" in plain language; generates keyword filter rules to review and enable.", "A dedicated \"Filter rules\" entry is not yet implemented.", "A rule review page is not yet implemented; artifacts appear under \"AI results\"."),
    "USAGE": ("AI Usage Dashboard", "Tracks today's / cumulative calls, input-output characters, failure rate and estimated cost.", "Settings - AI & diagnostics - \"AI usage\".", "A usage page: number cards + per-feature call ranking + daily-budget progress bar."),
    "TASK_QUEUE": ("AI Task Queue", "Queuing, rate limiting, retries and failure traces for all background AI tasks; clear and re-run by hand.", "Settings - AI & diagnostics - \"AI task queue\".", "A queue page: tasks grouped by status + failure reasons + retry/clear actions."),
    "PROMPT_TEMPLATE": ("Prompt Template Manager", "Central management for the global prompt and per-feed summary prompt overrides, with preview and reset.", "Settings - AI & diagnostics - \"Prompt templates\"; per-feed prompt on the feed action page.", "A manager page: built-in template preview + per-feed overrides (edit/clear), variable placeholders supported."),
}

# 小枚举：key -> 中文 -> 英文
MINOR = {
    "category": {
        "CONTENT": ("内容处理", "对单篇文章做理解、提炼与加工。", "Content processing", "Understand, distill and process individual articles."),
        "DISCOVERY": ("推荐发现", "帮你找到该读的、该订的、以及你还没看到的。", "Discovery", "Find what to read, what to subscribe to, and what you've missed."),
        "ASSIST": ("辅助推送", "总结、提醒与控制成本，让 AI 用得起。", "Assist", "Summaries, reminders and cost control that keep AI affordable."),
    },
    "scope": {
        "ARTICLE": ("文章", "Article"),
        "FEED": ("订阅源", "Feed"),
        "GLOBAL": ("全局", "Global"),
    },
    "trigger": {
        "MANUAL": ("手动触发", "用户点按钮才执行，不占用后台额度。", "Manual", "Runs only when you tap; doesn't use background quota."),
        "ON_DEMAND": ("按需生成", "有产物直接用，没有才生成，生成后持久保存。", "On demand", "Uses the saved result if present, otherwise generates once and persists."),
        "BATCH": ("后台批处理", "每日任务批量执行，受并发与日预算限制。", "Background batch", "Runs as daily batch jobs, limited by concurrency and daily budget."),
        "REALTIME": ("实时交互", "每次都实时调用，结果不落库。", "Realtime", "Calls the model every time; results are not persisted."),
    },
}


def xml_escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def parse_features():
    text = SRC.read_text(encoding="utf-8")
    body = text[text.index("enum class AiFeature("):text.index("enum class AiCategory")]
    entries = []
    for m in re.finditer(r"^    ([A-Z_]+)\(", body, re.M):
        name = m.group(1)
        start = m.start()
        nxt = re.search(r"^    [A-Z_]+\(", body[m.end():], re.M)
        seg = body[start:m.end() + (nxt.start() if nxt else len(body))]
        def field(f):
            fm = re.search(rf'{f} = "(.+?)"', seg)
            if not fm:
                sys.exit(f"[miss] {name}.{f}")
            return fm.group(1)
        entries.append((name, field("label"), field("summary"), field("entry"), field("presentation")))
    return entries


def main():
    feats = parse_features()
    if len(feats) != len(EN):
        sys.exit(f"enum {len(feats)} vs EN {len(EN)} 不一致: "
                 + str({n for n, *_ in feats} ^ set(EN)))
    # 校验中文与源码一致（防漂移），并准备 strings 条目
    adds = {}  # key -> (zh, en)
    for name, label, summary, entry, presentation in feats:
        zh = (label, summary, entry, presentation)
        for i, (field, z) in enumerate(zip(("label", "summary", "entry", "presentation"), zh)):
            adds[f"ai_feature_{name.lower()}_{field}"] = (z, EN[name][i])

    for kind in ("category", "scope", "trigger"):
        for name, vals in MINOR[kind].items():
            adds[f"ai_{kind}_{name.lower()}_label"] = (vals[0], vals[1])
            if kind != "scope":
                adds[f"ai_{kind}_{name.lower()}_description"] = (vals[2], vals[3])

    # 1) strings.xml 追加（幂等）
    for locale, idx in (("values", 0), ("values-en", 1)):
        xml = ROOT / f"app/src/main/res/{locale}/strings.xml"
        text = xml.read_text(encoding="utf-8")
        missing = [k for k in adds if f'name="{k}"' not in text]
        if not missing:
            print(f"{locale}: 已应用，跳过")
            continue
        lines = [f'    <string name="{k}">{xml_escape(adds[k][idx])}</string>' for k in missing]
        block = "\n".join(lines) + "\n"
        text = text.replace("</resources>", block + "</resources>")
        xml.write_text(text, encoding="utf-8")
        print(f"{locale}: +{len(missing)}")
        continue
        lines = [f"    <!-- i18n feature texts (ADR-0017) -->"]
        for key, (zh, en) in adds.items():
            lines.append(f'    <string name="{key}">{xml_escape((zh, en)[idx])}</string>')
        text = text.replace("</resources>", "\n".join(lines) + "\n</resources>")
        xml.write_text(text, encoding="utf-8")
        print(f"{locale}: +{len(adds)}")

    # 2) AiFeatureTexts.kt
    def when_body(prefix, pairs, indent="        "):
        return "\n".join(f"{indent}AiFeature.{name} -> R.string.{prefix}_{name.lower()}_{field}"
                         for name, *_ in pairs)

    kt = []
    kt.append("package com.cycling.rssradar.i18n")
    kt.append("")
    kt.append("import androidx.annotation.StringRes")
    kt.append("import com.cycling.rssradar.R")
    kt.append("import com.cycling.rssradar.core.data.ai.AiCategory")
    kt.append("import com.cycling.rssradar.core.data.ai.AiFeature")
    kt.append("import com.cycling.rssradar.core.data.ai.AiScope")
    kt.append("import com.cycling.rssradar.core.data.ai.AiTrigger")
    kt.append("")
    kt.append("/**")
    kt.append(" * AI 枚举文案的资源映射（ADR-0017）：core 层的中文 label/summary 等是数据口径，")
    kt.append(" * 界面展示一律走这里按当前语言取 res。新增功能必须同步补齐四条翻译。")
    kt.append(" */")
    kt.append("fun AiFeature.labelRes(): Int = when (this) {")
    kt.append(when_body("ai_feature", feats))
    kt.append("}")
    for field in ("summary", "entry", "presentation"):
        kt.append("")
        kt.append(f"fun AiFeature.{field}Res(): Int = when (this) {{")
        kt.append(when_body("ai_feature", feats, field=field) if False else
                  "\n".join(f"        AiFeature.{name} -> R.string.ai_feature_{name.lower()}_{field}"
                            for name, *_ in feats))
        kt.append("}")
    kt.append("")
    kt.append("fun AiCategory.labelRes(): Int = when (this) {")
    kt.append("\n".join(f"        AiCategory.{n} -> R.string.ai_category_{n.lower()}_label" for n in MINOR["category"]))
    kt.append("}")
    kt.append("")
    kt.append("fun AiCategory.descriptionRes(): Int = when (this) {")
    kt.append("\n".join(f"        AiCategory.{n} -> R.string.ai_category_{n.lower()}_description" for n in MINOR["category"]))
    kt.append("}")
    kt.append("")
    kt.append("fun AiScope.labelRes(): Int = when (this) {")
    kt.append("\n".join(f"        AiScope.{n} -> R.string.ai_scope_{n.lower()}_label" for n in MINOR["scope"]))
    kt.append("}")
    kt.append("")
    kt.append("fun AiTrigger.labelRes(): Int = when (this) {")
    kt.append("\n".join(f"        AiTrigger.{n} -> R.string.ai_trigger_{n.lower()}_label" for n in MINOR["trigger"]))
    kt.append("}")
    kt.append("")
    kt.append("fun AiTrigger.descriptionRes(): Int = when (this) {")
    kt.append("\n".join(f"        AiTrigger.{n} -> R.string.ai_trigger_{n.lower()}_description" for n in MINOR["trigger"]))
    kt.append("}")
    kt.append("")

    out = ROOT / "app/src/main/java/com/cycling/rssradar/i18n/AiFeatureTexts.kt"
    out.write_text("\n".join(kt), encoding="utf-8")
    print(f"written {out.name}: {len(feats)} features")


if __name__ == "__main__":
    main()
