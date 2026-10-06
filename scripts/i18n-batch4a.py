# -*- coding: utf-8 -*-
"""i18n 批4a：ui/feed 文案资源化（ADR-0017）。

结构化改动（非纯字面量替换）：
- ContentTypeFilter：`label` / `emptyCopy()` 从枚举里摘掉，改由 i18n/FeedTexts 映射资源 id。
- PagedSnapshot：日期头文案抽成 `CalendarDayLabels` 纯数据结构，`dayGroups` /
  `calendarDayLabel` 不再自带中文（保持无 Android 依赖、JVM 可测）。
- FeedListViewModel / FeedArticlesViewModel：`uiMessage` 由 String? 改 `UiText?`。

幂等：已应用的文件/资源会跳过。
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = "app/src/main/java/com/cycling/rssradar"
FEED = f"{APP}/ui/feed"

# ── 文案表：key -> (中文, English) ────────────────────────────────────────────
STR = {
    # 内容类型
    "ctype_image": ("图片", "Images"),
    "ctype_video": ("视频", "Videos"),
    "ctype_audio": ("音频", "Audio"),
    "ctype_empty_title": ("「%1$s」分区还没有源", "Nothing in the \"%1$s\" section yet"),
    "ctype_empty_desc": ("订阅%1$s类源后，在这里聚合浏览", "Subscribe to %1$s feeds and they'll gather here"),
    # 空态
    "feed_empty_no_feeds": ("还没有订阅", "No subscriptions yet"),
    "feed_empty_no_feeds_desc": ("去订阅页添加你的第一个 RSS / Atom 源", "Add your first RSS or Atom feed on the subscriptions page"),
    "feed_empty_unread": ("没有未读文章", "Nothing unread"),
    "feed_empty_unread_desc": ("所有文章都看完了，休息一下", "You're all caught up — take a break"),
    "feed_empty_starred": ("还没有收藏", "Nothing starred"),
    "feed_empty_starred_desc": ("阅读时点击星标，把好文章留下来", "Tap the star while reading to keep good articles"),
    "feed_empty_readlater": ("暂无稍后读", "Nothing saved for later"),
    "feed_empty_readlater_desc": ("阅读时点击书签，稍后再看", "Tap the bookmark while reading to save it for later"),
    "feed_empty_recommended": ("暂无推荐", "No recommendations yet"),
    "feed_empty_recommended_desc": ("最近未读都读完了，或还没有订阅源", "Recent unread is exhausted, or you have no feeds yet"),
    "feed_empty_add": ("添加订阅源", "Add a feed"),
    "feed_empty_import_opml": ("导入 OPML 订阅", "Import OPML subscriptions"),
    "feed_ranking": ("正在按你的阅读偏好排序…", "Ranking by your reading preferences…"),
    # 日历日文案包
    "day_today": ("今天", "Today"),
    "day_yesterday": ("昨天", "Yesterday"),
    "day_2ago": ("前天", "Two days ago"),
    "day_unknown": ("未知日期", "Unknown date"),
    "day_monthday": ("%1$s%2$s日", "%1$s %2$s"),
    "day_monthday_weekday": ("%1$s %2$s", "%1$s, %2$s"),
    "day_yearmonthday": ("%1$s年%2$s%3$s日", "%2$s %3$s, %1$s"),
    # 单源文章列表
    "feed_articles_title": ("订阅源", "Feed"),
    "feed_articles_refresh": ("刷新此源", "Refresh this feed"),
    "feed_articles_empty": ("此订阅源还没有文章", "No articles in this feed yet"),
    "feed_articles_refresh_now": ("立即刷新", "Refresh now"),
    # 信息流
    "feed_reduce_such": ("已减少此订阅源的推荐", "Showing fewer recommendations from this feed"),
    "undo": ("撤销", "Undo"),
    "feed_deleted_article": ("已删除「%1$s」", "Deleted \"%1$s\""),
    "view_mode_title": ("视图模式", "Layout"),
    "vm_list_desc": ("单列紧凑，标题 + 摘要", "Single column: title + summary"),
    "vm_card_desc": ("卡片排版，右侧缩略图", "Cards with a thumbnail on the right"),
    "vm_magazine_desc": ("图文混排，首篇大图突出", "Magazine: the first item gets a large image"),
    "vm_grid_desc": ("多列网格，按屏幕宽度自适应", "Grid: column count adapts to screen width"),
    "mark_read_title": ("标记已读", "Mark as read"),
    "mark_read_all_desc": ("把全部文章标为已读", "Mark every article as read"),
    "mark_read_before_desc": ("把 %1$s 发布的未读文章标为已读", "Mark unread articles published %1$s as read"),
    "action_search": ("搜索", "Search"),
    "more_actions": ("更多操作", "More actions"),
    "view_mode_value": ("视图模式 · %1$s", "Layout · %1$s"),
    "group_filter_active": ("分组筛选 · 已启用", "Group filter · on"),
    "group_filter": ("分组筛选", "Group filter"),
    "tab_unread_count": ("未读 %1$s", "Unread %1$s"),
    "tab_starred": ("收藏", "Starred"),
    "tab_read_later": ("稍后读", "Read later"),
    "tab_recommended": ("推荐", "Recommended"),
    "filter_title": ("筛选", "Filter"),
    "content_type": ("内容类型", "Content type"),
    "group_title": ("分组", "Group"),
    "cd_selected": ("已选", "Selected"),
    "star_off": ("取消收藏", "Unstar"),
    "star_on": ("收藏", "Star"),
    "mark_unread": ("标未读", "Mark unread"),
    "mark_read": ("标已读", "Mark read"),
    "cd_cover": ("封面缩略图", "Cover thumbnail"),
    # VM 消息（UiText）
    "feed_marked_read": ("已标记 %1$s 篇为已读", "Marked %1$s article(s) as read"),
    "feed_nothing_to_mark": ("没有需要标记的文章", "Nothing to mark as read"),
    "feed_refresh_failed": ("刷新失败，展示的是上次内容", "Refresh failed — showing what was loaded before"),
    "feed_refreshed": ("已更新 %1$s 个订阅源", "Updated %1$s feed(s)"),
    "add_feed_success": ("订阅成功", "Subscribed"),
    "add_feed_duplicate": ("该源已订阅", "This feed is already subscribed"),
    "add_feed_invalid": ("不是有效的 RSS/Atom 源", "Not a valid RSS/Atom feed"),
    "add_feed_network": ("网络错误，请检查链接后重试", "Network error — check the link and retry"),
    # 标记已读条件
    "mark_cond_1d": ("1 天前", "1 day ago"),
    "mark_cond_3d": ("3 天前", "3 days ago"),
    "mark_cond_7d": ("7 天前", "7 days ago"),
}
# 星期与月份（日历日文案包用）
for i, (zh, en) in enumerate(
    [("周一", "Mon"), ("周二", "Tue"), ("周三", "Wed"), ("周四", "Thu"),
     ("周五", "Fri"), ("周六", "Sat"), ("周日", "Sun")], start=1
):
    STR[f"weekday_{i}"] = (zh, en)
for i, en in enumerate(
    ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], start=1
):
    STR[f"month_{i}"] = (f"{i}月", en)


def xml_escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def ensure_imports(text, *imports):
    for imp in imports:
        if f"import {imp}\n" not in text:
            anchor = "import com.cycling.rssradar.R\n"
            if anchor in text:
                text = text.replace(anchor, anchor + f"import {imp}\n", 1)
            else:
                text = text.replace("import androidx.compose.foundation",
                                    f"import {imp}\nimport androidx.compose.foundation", 1)
    if "import com.cycling.rssradar.R\n" not in text:
        text = text.replace("import androidx.compose.ui.res.stringResource\n",
                            "import androidx.compose.ui.res.stringResource\nimport com.cycling.rssradar.R\n", 1)
    if "import androidx.compose.ui.res.stringResource" not in text:
        text = text.replace("import androidx.compose.foundation",
                            "import androidx.compose.ui.res.stringResource\nimport androidx.compose.foundation", 1)
    return text


def write(path, text):
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: ok")


# ── 1. i18n 映射文件 ─────────────────────────────────────────────────────────
def make_feed_texts():
    p = ROOT / APP / "i18n/FeedTexts.kt"
    if p.exists():
        print("FeedTexts: 已存在")
        return
    p.write_text('''package com.cycling.rssradar.i18n

import com.cycling.rssradar.R
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.ui.feed.ContentTypeFilter

/**
 * 信息流与订阅域的枚举 → 文案资源映射（ADR-0017 §3）。
 *
 * 枚举本身不带中文：core 与 ui 的枚举保持纯数据，翻译只在 UI 层按当前语言取，
 * 纯 JVM 测试因此不碰 android 资源。
 */
fun ContentTypeFilter.labelRes(): Int = when (this) {
    ContentTypeFilter.All -> R.string.filter_all
    ContentTypeFilter.Image -> R.string.ctype_image
    ContentTypeFilter.Video -> R.string.ctype_video
    ContentTypeFilter.Audio -> R.string.ctype_audio
}

fun ContentTypeFilter.emptyTitleRes(): Int = R.string.ctype_empty_title

fun ContentTypeFilter.emptyDescRes(): Int = R.string.ctype_empty_desc

fun MarkAsReadCondition.labelRes(): Int = when (this) {
    MarkAsReadCondition.ONE_DAY -> R.string.mark_cond_1d
    MarkAsReadCondition.THREE_DAYS -> R.string.mark_cond_3d
    MarkAsReadCondition.SEVEN_DAYS -> R.string.mark_cond_7d
    MarkAsReadCondition.ALL -> R.string.filter_all
}
''', encoding="utf-8")
    print("FeedTexts: ok")


# ── 2. ContentTypeFilter：摘掉中文 ───────────────────────────────────────────
def patch_content_type_filter():
    p = ROOT / FEED / "ContentTypeFilter.kt"
    t = p.read_text(encoding="utf-8")
    if "emptyCopy" not in t:
        print("ContentTypeFilter: 已应用")
        return
    old = '''enum class ContentTypeFilter(val dbValue: Int?, val label: String) {
    All(null, "全部"),
    Image(FeedEntity.CONTENT_TYPE_IMAGE, "图片"),
    Video(FeedEntity.CONTENT_TYPE_VIDEO, "视频"),
    Audio(FeedEntity.CONTENT_TYPE_AUDIO, "音频");

    /**
     * 空分区空态文案（纯函数，可测）：「订阅 XX 类源后在此聚合」。
     * 「全部」为空 = 还没有任何订阅，文案沿用 All tab 现有口径。
     */
    fun emptyCopy(): Pair<String, String> = when (this) {
        Image -> "「图片」分区还没有源" to "订阅图片类源后，在这里聚合浏览"
        Video -> "「视频」分区还没有源" to "订阅视频类源后，在这里聚合浏览"
        Audio -> "「音频」分区还没有源" to "订阅音频类源后，在这里聚合浏览"
        All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
    }
}'''
    new = '''enum class ContentTypeFilter(val dbValue: Int?) {
    All(null),
    Image(FeedEntity.CONTENT_TYPE_IMAGE),
    Video(FeedEntity.CONTENT_TYPE_VIDEO),
    Audio(FeedEntity.CONTENT_TYPE_AUDIO),
}'''
    assert old in t, "ContentTypeFilter 块不匹配"
    write(p, t.replace(old, new))


# ── 3. PagedSnapshot：日期头文案包 ───────────────────────────────────────────
def patch_paged_snapshot():
    p = ROOT / FEED / "PagedSnapshot.kt"
    t = p.read_text(encoding="utf-8")
    if "CalendarDayLabels" in t:
        print("PagedSnapshot: 已应用")
        return

    old_daygroups = '''fun dayGroups(
    articles: List<ArticleWithFeed>,
    labelOf: (Long) -> String = { day -> calendarDayLabel(day) },
): List<DayGroup> {'''
    new_daygroups = '''fun dayGroups(
    articles: List<ArticleWithFeed>,
    labels: CalendarDayLabels,
    today: Long = LocalDate.now().toEpochDay(),
): List<DayGroup> {'''
    assert old_daygroups in t, "dayGroups 签名不匹配"
    t = t.replace(old_daygroups, new_daygroups)
    t = t.replace("            DayGroup(key = day, label = labelOf(day), items = list.map { it.second })",
                  "            DayGroup(key = day, label = calendarDayLabel(day, today, labels), items = list.map { it.second })")
    t = t.replace('return if (undated.isEmpty()) groups else groups + DayGroup(UNDATED_DAY_KEY, "未知日期", undated)',
                  'return if (undated.isEmpty()) groups else groups + DayGroup(UNDATED_DAY_KEY, labels.unknown, undated)')

    old_label = '''private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")'''
    new_label = '''/**
 * 日历日文案包（ADR-0017 §3）：纯 JVM 数据结构，生产端由 Composable 用
 * `stringResource` 装配后传入，测试端直接给常量——[calendarDayLabel] 因此
 * 保持零 Android 依赖，也就不必在纯 JVM 代码里硬编码任何语言的文案。
 */
data class CalendarDayLabels(
    val today: String,
    val yesterday: String,
    val twoDaysAgo: String,
    val unknown: String,
    val weekdays: List<String>,
    val months: List<String>,
    /** 月日，参数：(月份名, 日)。中英占位符顺序不同，故整串进资源。 */
    val monthDay: String,
    /** 月日 + 周几，参数：(月日串, 周几)。 */
    val monthDayWeekday: String,
    /** 年月日，参数：(年, 月份名, 日)。 */
    val yearMonthDay: String,
)'''
    assert old_label in t, "WEEKDAYS 不匹配"
    t = t.replace(old_label, new_label)

    old_fn = '''fun calendarDayLabel(day: Long, today: Long = LocalDate.now().toEpochDay()): String {
    when (today - day) {
        0L -> return "今天"
        1L -> return "昨天"
        2L -> return "前天"
    }
    val date = LocalDate.ofEpochDay(day)
    // 一周内带周几，读起来最快；超出一周只有日期。diff 为负（源的时间戳超前，
    // 时区或源站时钟问题）不给「明天」这种假答案，一律落到绝对日期。
    return if (today - day in 3..6) {
        "${date.monthValue}月${date.dayOfMonth}日 ${WEEKDAYS[date.dayOfWeek.value - 1]}"
    } else if (date.year == LocalDate.ofEpochDay(today).year) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}'''
    new_fn = '''fun calendarDayLabel(
    day: Long,
    today: Long = LocalDate.now().toEpochDay(),
    labels: CalendarDayLabels,
): String {
    when (today - day) {
        0L -> return labels.today
        1L -> return labels.yesterday
        2L -> return labels.twoDaysAgo
    }
    val date = LocalDate.ofEpochDay(day)
    // 一周内带周几，读起来最快；超出一周只有日期。diff 为负（源的时间戳超前，
    // 时区或源站时钟问题）不给「明天」这种假答案，一律落到绝对日期。
    val monthDay = labels.monthDay.format(labels.months[date.monthValue - 1], date.dayOfMonth)
    return if (today - day in 3..6) {
        labels.monthDayWeekday.format(monthDay, labels.weekdays[date.dayOfWeek.value - 1])
    } else if (date.year == LocalDate.ofEpochDay(today).year) {
        monthDay
    } else {
        labels.yearMonthDay.format(date.year, labels.months[date.monthValue - 1], date.dayOfMonth)
    }
}'''
    assert old_fn in t, "calendarDayLabel 不匹配"
    t = t.replace(old_fn, new_fn)
    write(p, t)


# ── 4. 测试：改用文案包 ──────────────────────────────────────────────────────
def patch_paged_snapshot_test():
    p = ROOT / "app/src/test/java/com/cycling/rssradar/ui/feed/PagedSnapshotTest.kt"
    t = p.read_text(encoding="utf-8")
    if "TEST_DAY_LABELS" in t:
        print("PagedSnapshotTest: 已应用")
        return
    t = t.replace('''        val groups = dayGroups(
            listOf(article(1, day1b), article(2, day1a), article(3, day2)),
            labelOf = { "D$it" },
        )''', '''        val groups = dayGroups(
            listOf(article(1, day1b), article(2, day1a), article(3, day2)),
            TEST_DAY_LABELS,
        )''')
    t = t.replace('''        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null)),
            labelOf = { "D" },
        )''', '''        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null)),
            TEST_DAY_LABELS,
        )''')
    t = t.replace('''val groups = dayGroups(listOf(article(1, null), article(2, null)), labelOf = { "D" })''',
                  '''val groups = dayGroups(listOf(article(1, null), article(2, null)), TEST_DAY_LABELS)''')
    t = t.replace('''        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null), article(3, 9_000_000L)),
        )''', '''        val groups = dayGroups(
            listOf(article(1, 5_000_000L), article(2, null), article(3, 9_000_000L)),
            TEST_DAY_LABELS,
        )''')
    t = re.sub(r'calendarDayLabel\(([^()]*?(?:\([^()]*\))?[^()]*?), today\)',
               r'calendarDayLabel(\1, today, TEST_DAY_LABELS)', t)
    t = t.replace('val labels = listOf(today - 1, today).map { calendarDayLabel(it, today) }',
                  'val labels = listOf(today - 1, today).map { calendarDayLabel(it, today, TEST_DAY_LABELS) }')
    fixture = '''
/** 中文文案包夹具：主源码不再自带任何语言的文案，测试自己带（ADR-0017 §3）。 */
private val TEST_DAY_LABELS = CalendarDayLabels(
    today = "今天",
    yesterday = "昨天",
    twoDaysAgo = "前天",
    unknown = "未知日期",
    weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
    months = (1..12).map { "${it}月" },
    monthDay = "%1$s%2$s日",
    monthDayWeekday = "%1$s %2$s",
    yearMonthDay = "%1$s年%2$s%3$s日",
)
'''
    t = t.replace("/** 日期头文案：按日历日算，不按相对时长算（回归用例见「跨午夜」）。 */",
                  fixture + "\n/** 日期头文案：按日历日算，不按相对时长算（回归用例见「跨午夜」）。 */", 1)
    write(p, t)


# ── 5. 单源页 VM / 页 ────────────────────────────────────────────────────────
def patch_feed_articles_vm():
    p = ROOT / FEED / "FeedArticlesViewModel.kt"
    t = p.read_text(encoding="utf-8")
    if "UiText?" in t:
        print("FeedArticlesViewModel: 已应用")
        return
    t = t.replace("var uiMessage by mutableStateOf<String?>(null)",
                  "var uiMessage by mutableStateOf<UiText?>(null)")
    t = t.replace('if (!ok) uiMessage = "刷新失败，展示的是上次内容"',
                  "if (!ok) uiMessage = UiText.res(R.string.feed_refresh_failed)")
    t = ensure_imports(t, "com.cycling.rssradar.i18n.UiText")
    write(p, t)


def patch_feed_articles_screen():
    p = ROOT / FEED / "FeedArticlesScreen.kt"
    t = p.read_text(encoding="utf-8")
    if "R.string.feed_articles_title" in t:
        print("FeedArticlesScreen: 已应用")
        return
    pairs = [
        ('"订阅源"', "stringResource(R.string.feed_articles_title)"),
        ('"返回"', "stringResource(R.string.back)"),
        ('"刷新此源"', "stringResource(R.string.feed_articles_refresh)"),
        ('"此订阅源还没有文章"', "stringResource(R.string.feed_articles_empty)"),
        ('"立即刷新"', "stringResource(R.string.feed_articles_refresh_now)"),
        ("snackbarHostState.showSnackbar(it)", "snackbarHostState.showSnackbar(it.resolve())"),
    ]
    for old, new in pairs:
        assert old in t, f"FeedArticlesScreen 缺 {old}"
        t = t.replace(old, new)
    t = ensure_imports(t, "com.cycling.rssradar.i18n.resolve")
    write(p, t)


# ── 6. 信息流 VM ─────────────────────────────────────────────────────────────
def patch_feed_list_vm():
    p = ROOT / FEED / "FeedListViewModel.kt"
    t = p.read_text(encoding="utf-8")
    if "UiText?" in t:
        print("FeedListViewModel: 已应用")
        return
    t = t.replace("val uiMessage: String? = null,", "val uiMessage: UiText? = null,")
    t = t.replace('uiMessage = if (count > 0) "已标记 $count 篇为已读" else "没有需要标记的文章",',
                  'uiMessage = if (count > 0) UiText.res(R.string.feed_marked_read, count)\n                    else UiText.res(R.string.feed_nothing_to_mark),')
    t = t.replace('''                        hasFeeds && successCount == 0 -> "刷新失败，展示的是上次内容"
                        successCount > 0 -> "已更新 $successCount 个订阅源"''',
                  '''                        hasFeeds && successCount == 0 -> UiText.res(R.string.feed_refresh_failed)
                        successCount > 0 -> UiText.res(R.string.feed_refreshed, successCount)''')
    t = t.replace('''                AddFeedResult.Success -> "订阅成功"
                AddFeedResult.Duplicate -> "该源已订阅"
                AddFeedResult.InvalidFeed -> "不是有效的 RSS/Atom 源"
                AddFeedResult.NetworkError -> "网络错误，请检查链接后重试"''',
                  '''                AddFeedResult.Success -> UiText.res(R.string.add_feed_success)
                AddFeedResult.Duplicate -> UiText.res(R.string.add_feed_duplicate)
                AddFeedResult.InvalidFeed -> UiText.res(R.string.add_feed_invalid)
                AddFeedResult.NetworkError -> UiText.res(R.string.add_feed_network)''')
    t = ensure_imports(t, "com.cycling.rssradar.i18n.UiText")
    write(p, t)


# ── 7. 信息流页 ──────────────────────────────────────────────────────────────
def patch_feed_list_screen():
    p = ROOT / FEED / "FeedListScreen.kt"
    t = p.read_text(encoding="utf-8")
    if "R.string.feed_reduce_such" in t:
        print("FeedListScreen: 已应用")
        return

    blocks = [
        # Snackbar
        ('message = "已减少此订阅源的推荐",', 'message = stringResource(R.string.feed_reduce_such),'),
        ('actionLabel = "撤销",', 'actionLabel = stringResource(R.string.undo),', 2),
        ('message = "已删除「${deleted.title}」",',
         'message = stringResource(R.string.feed_deleted_article, deleted.title),'),
        ("snackbarHostState.showSnackbar(it)", "snackbarHostState.showSnackbar(it.resolve())"),
        # 视图模式弹层
        ('            title = "视图模式",', '            title = stringResource(R.string.view_mode_title),'),
        ('''            options = ListViewMode.entries.toList(),
            selected = viewMode,
            label = { it.label },
            subtitle = { mode ->
                when (mode) {
                    ListViewMode.LIST -> "单列紧凑，标题 + 摘要"
                    ListViewMode.CARD -> "卡片排版，右侧缩略图"
                    ListViewMode.MAGAZINE -> "图文混排，首篇大图突出"
                    ListViewMode.GRID -> "多列网格，按屏幕宽度自适应"
                }
            },''',
         '''            options = ListViewMode.entries.toList(),
            selected = viewMode,
            label = { viewModeLabels.getValue(it) },
            subtitle = { mode ->
                when (mode) {
                    ListViewMode.LIST -> stringResource(R.string.vm_list_desc)
                    ListViewMode.CARD -> stringResource(R.string.vm_card_desc)
                    ListViewMode.MAGAZINE -> stringResource(R.string.vm_magazine_desc)
                    ListViewMode.GRID -> stringResource(R.string.vm_grid_desc)
                }
            },'''),
        ('    if (showViewModeSheet) {\n        OptionPickerSheet(',
         '    if (showViewModeSheet) {\n        val viewModeLabels = ListViewMode.entries.associateWith { stringResource(it.labelRes()) }\n        OptionPickerSheet('),
        # 标记已读弹层
        ('            title = "标记已读",', '            title = stringResource(R.string.mark_read_title),'),
        ('''            options = MarkAsReadCondition.entries.toList(),
            selected = null,
            label = { it.label },
            subtitle = { condition ->
                when (condition) {
                    MarkAsReadCondition.ALL -> "把全部文章标为已读"
                    else -> "把 ${condition.label}发布的未读文章标为已读"
                }
            },''',
         '''            options = MarkAsReadCondition.entries.toList(),
            selected = null,
            label = { markReadLabels.getValue(it) },
            subtitle = { condition ->
                if (condition == MarkAsReadCondition.ALL) {
                    stringResource(R.string.mark_read_all_desc)
                } else {
                    stringResource(R.string.mark_read_before_desc, markReadLabels.getValue(condition))
                }
            },'''),
        ('    if (showMarkReadSheet) {\n        OptionPickerSheet(',
         '    if (showMarkReadSheet) {\n        val markReadLabels = MarkAsReadCondition.entries.associateWith { stringResource(it.labelRes()) }\n        OptionPickerSheet('),
        # 顶栏
        ('contentDescription = "搜索"', 'contentDescription = stringResource(R.string.action_search)'),
        ('contentDescription = "更多操作"', 'contentDescription = stringResource(R.string.more_actions)'),
        ('text = { Text("标记已读") }', 'text = { Text(stringResource(R.string.mark_read_title)) }'),
        ('''                        Text(
                            buildString {
                                append("视图模式 · ")
                                append(
                                    when (viewMode) {
                                        ListViewMode.LIST -> "列表"
                                        ListViewMode.CARD -> "卡片"
                                        ListViewMode.MAGAZINE -> "杂志"
                                        ListViewMode.GRID -> "网格"
                                    },
                                )
                            },
                        )''',
         '''                        Text(
                            stringResource(
                                R.string.view_mode_value,
                                stringResource(viewMode.labelRes()),
                            ),
                        )'''),
        ('text = { Text(if (filterActive) "分组筛选 · 已启用" else "分组筛选") }',
         'text = { Text(if (filterActive) stringResource(R.string.group_filter_active) else stringResource(R.string.group_filter)) }'),
        # Tab
        ('''                    FeedTab.All -> "全部"
                    FeedTab.Unread -> "未读 $unreadCount"
                    FeedTab.Starred -> "收藏"
                    FeedTab.Bookmarked -> "稍后读"
                    FeedTab.Recommended -> "推荐"''',
         '''                    FeedTab.All -> stringResource(R.string.filter_all)
                    FeedTab.Unread -> stringResource(R.string.tab_unread_count, unreadCount)
                    FeedTab.Starred -> stringResource(R.string.tab_starred)
                    FeedTab.Bookmarked -> stringResource(R.string.tab_read_later)
                    FeedTab.Recommended -> stringResource(R.string.tab_recommended)'''),
        # 筛选弹层
        ('                text = "筛选",', '                text = stringResource(R.string.filter_title),'),
        ('                text = "内容类型",', '                text = stringResource(R.string.content_type),'),
        ('                text = "分组",', '                text = stringResource(R.string.group_title),'),
        ('                        label = "全部",', '                        label = stringResource(R.string.filter_all),'),
        ('                contentDescription = "已选",', '                contentDescription = stringResource(R.string.cd_selected),'),
        # 媒体角标（8 处同字面量）
        ('"视频"', 'stringResource(R.string.ctype_video)', 4),
        ('"音频"', 'stringResource(R.string.ctype_audio)', 4),
        # 滑动动作
        ('if (isStarred) "取消收藏" else "收藏",',
         'if (isStarred) stringResource(R.string.star_off) else stringResource(R.string.star_on),'),
        ('if (isRead) "标未读" else "标已读",',
         'if (isRead) stringResource(R.string.mark_unread) else stringResource(R.string.mark_read),'),
        ('contentDescription = "封面缩略图"', 'contentDescription = stringResource(R.string.cd_cover)'),
        # 空态
        ('''    val (title, hint) = if (partitionEmpty && selectedContentType != ContentTypeFilter.All) {
        selectedContentType.emptyCopy()
    } else {
        when (selectedTab) {
            FeedTab.All -> "还没有订阅" to "去订阅页添加你的第一个 RSS / Atom 源"
            FeedTab.Unread -> "没有未读文章" to "所有文章都看完了，休息一下"
            FeedTab.Starred -> "还没有收藏" to "阅读时点击星标，把好文章留下来"
            FeedTab.Bookmarked -> "暂无稍后读" to "阅读时点击书签，稍后再看"
            // 推荐流空态（ADR-0013）：候选池 = 未读 + 14 天窗，读完就没了——如实说，不编内容
            FeedTab.Recommended -> "暂无推荐" to "最近未读都读完了，或还没有订阅源"
        }
    }''',
         '''    val (title, hint) = if (partitionEmpty && selectedContentType != ContentTypeFilter.All) {
        val typeName = stringResource(selectedContentType.labelRes())
        stringResource(R.string.ctype_empty_title, typeName) to
            stringResource(R.string.ctype_empty_desc, typeName)
    } else {
        when (selectedTab) {
            FeedTab.All ->
                stringResource(R.string.feed_empty_no_feeds) to
                    stringResource(R.string.feed_empty_no_feeds_desc)
            FeedTab.Unread ->
                stringResource(R.string.feed_empty_unread) to
                    stringResource(R.string.feed_empty_unread_desc)
            FeedTab.Starred ->
                stringResource(R.string.feed_empty_starred) to
                    stringResource(R.string.feed_empty_starred_desc)
            FeedTab.Bookmarked ->
                stringResource(R.string.feed_empty_readlater) to
                    stringResource(R.string.feed_empty_readlater_desc)
            // 推荐流空态（ADR-0013）：候选池 = 未读 + 14 天窗，读完就没了——如实说，不编内容
            FeedTab.Recommended ->
                stringResource(R.string.feed_empty_recommended) to
                    stringResource(R.string.feed_empty_recommended_desc)
        }
    }'''),
        ('Text("添加订阅源")', 'Text(stringResource(R.string.feed_empty_add))'),
        ('Text("导入 OPML 订阅")', 'Text(stringResource(R.string.feed_empty_import_opml))'),
        ('Text("正在按你的阅读偏好排序…", color = radarColors().textSecondary, style = MaterialTheme.typography.bodyMedium)',
         'Text(stringResource(R.string.feed_ranking), color = radarColors().textSecondary, style = MaterialTheme.typography.bodyMedium)'),
        # 日期分组：生产端装配文案包
        ('''    val dayGroups = if (display.stickyDateHeader) {
        remember(articles) { dayGroups(articles) }''',
         '''    val dayLabels = CalendarDayLabels(
        today = stringResource(R.string.day_today),
        yesterday = stringResource(R.string.day_yesterday),
        twoDaysAgo = stringResource(R.string.day_2ago),
        unknown = stringResource(R.string.day_unknown),
        weekdays = listOf(
            R.string.weekday_1, R.string.weekday_2, R.string.weekday_3, R.string.weekday_4,
            R.string.weekday_5, R.string.weekday_6, R.string.weekday_7,
        ).map { stringResource(it) },
        months = listOf(
            R.string.month_1, R.string.month_2, R.string.month_3, R.string.month_4,
            R.string.month_5, R.string.month_6, R.string.month_7, R.string.month_8,
            R.string.month_9, R.string.month_10, R.string.month_11, R.string.month_12,
        ).map { stringResource(it) },
        monthDay = stringResource(R.string.day_monthday),
        monthDayWeekday = stringResource(R.string.day_monthday_weekday),
        yearMonthDay = stringResource(R.string.day_yearmonthday),
    )
    val dayGroups = if (display.stickyDateHeader) {
        remember(articles, dayLabels) { dayGroups(articles, dayLabels) }'''),
    ]
    for item in blocks:
        old, new = item[0], item[1]
        expect = item[2] if len(item) > 2 else 1
        n = t.count(old)
        assert n == expect, f"FeedListScreen: {old[:50]!r} 命中 {n}（期望 {expect}）"
        t = t.replace(old, new)
    t = ensure_imports(
        t,
        "com.cycling.rssradar.i18n.labelRes",
        "com.cycling.rssradar.i18n.resolve",
        "com.cycling.rssradar.i18n.UiText",
    )
    write(p, t)


# ── 8. strings.xml ───────────────────────────────────────────────────────────
def append_strings():
    existing = set()
    for locale in ("values", "values-en"):
        xml = ROOT / f"app/src/main/res/{locale}/strings.xml"
        existing |= set(re.findall(r'<string name="([^"]+)"', xml.read_text(encoding="utf-8")))
    dup = sorted(set(STR) & existing)
    if dup:
        print(f"[warn] 已存在、将跳过：{dup}")
    table = {k: v for k, v in STR.items() if k not in existing}
    for locale, idx in (("values", 0), ("values-en", 1)):
        xml = ROOT / f"app/src/main/res/{locale}/strings.xml"
        t = xml.read_text(encoding="utf-8")
        if "i18n batch 4a" in t:
            print(f"{locale}: 已应用")
            continue
        lines = ["    <!-- i18n batch 4a: ui/feed -->"]
        for key, pair in table.items():
            lines.append(f'    <string name="{key}">{xml_escape(pair[idx])}</string>')
        t = t.replace("</resources>", "\n".join(lines) + "\n</resources>")
        xml.write_text(t, encoding="utf-8")
        print(f"{locale}: +{len(table)}")


def main():
    make_feed_texts()
    patch_content_type_filter()
    patch_paged_snapshot()
    patch_paged_snapshot_test()
    patch_feed_articles_vm()
    patch_feed_articles_screen()
    patch_feed_list_vm()
    patch_feed_list_screen()
    append_strings()


if __name__ == "__main__":
    main()
