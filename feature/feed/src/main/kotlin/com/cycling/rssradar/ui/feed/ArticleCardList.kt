package com.cycling.rssradar.ui.feed

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.ui.components.tabBarBottomClearance
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.effectsSpec
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.LocalListDisplay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)

@Composable

fun ArticleCardList(
    articles: List<ArticleWithFeed>,
    onArticleClick: (ArticleWithFeed) -> Unit,
    onToggleRead: (Long, Boolean) -> Unit,
    onToggleStarred: (Long) -> Unit,
    onToggleBookmarked: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onScrolledToEnd: () -> Unit,
    /**
     * 「减少此类」（ADR-0013）：非空时卡片的上下文菜单出现该动作。
     * 只有推荐流传——其余列表的排序与画像无关，负反馈无处落地。
     */
    onReduceSuch: ((Long) -> Unit)? = null,
    // 底部让位：tab 屏传底部导航栏让位；无导航栏的页面传普通间距
    bottomPadding: Dp = tabBarBottomClearance(),
    // 单源页强制隐藏订阅源名称（同源重复是噪音）；null = 跟随全局配置
    showFeedName: Boolean? = null,
    /** 视图模式覆盖（单源页用）：null = 跟随全局；非 null 时无视全局模式。 */
    viewModeOverride: ListViewMode? = null,
    /**
     * 滚动自动标记已读（#11）：上报"已滚出视口顶部"的文章 id 批次。
     * 由 [LocalListDisplay] 的开关决定是否启用，关闭时本回调不会被调用。
     */
    markReadPassed: (List<Long>) -> Unit = {},
    /**
     * 当前筛选下的文章总数（滚动指示条分母）：翻页追加时不变，thumb 稳定。
     * null = 总数未知（推荐流/单源页），退回按已加载量估算。
     */
    totalCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    val display = LocalListDisplay.current.let {
        it.copy(
            showFeedName = showFeedName ?: it.showFeedName,
            viewMode = viewModeOverride ?: it.viewMode,
        )
    }
    // 网格模式是独立容器（LazyVerticalGrid），走自己的渲染分支；粘性日期头与
    // 滚动标已读都是 LazyColumn 槽位逻辑，网格里不适用（与图片画廊同规则）。
    if (display.viewMode == ListViewMode.GRID) {
        ArticleAdaptiveGrid(
            articles = articles,
            onArticleClick = onArticleClick,
            onScrolledToEnd = onScrolledToEnd,
            bottomPadding = bottomPadding,
            onReduceSuch = onReduceSuch,
            onToggleRead = onToggleRead,
            onToggleStarred = onToggleStarred,
            onToggleBookmarked = onToggleBookmarked,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    // 列表模式 = 单列紧凑：固定无缩略图（摘要保留），其余显示项沿用用户设置
    val effective = when (display.viewMode) {
        ListViewMode.LIST -> display.copy(showThumbnail = false)
        else -> display
    }
    val listState = rememberLazyListState()
    // 删除淡出（docs/motion.md #4）：数万条列表只做 fadeOut，placement / fadeIn 关闭
    // ——低端机上 placement 是帧率杀手。reduce-motion 时 fadeOut 也关（红线）。
    val reducedMotion = LocalReducedMotion.current
    val removeFadeSpec: FiniteAnimationSpec<Float>? =
        if (reducedMotion) null else effectsSpec()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrolledToEnd()
    }
    // 分组必须在 LazyColumn builder 外算：builder lambda 不是 composable 上下文，
    // remember 放里面编不过。不开启粘性头时不算，零开销。
    val dayLabels = CalendarDayLabels(
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
        remember(articles, dayLabels) { dayGroups(articles, dayLabels) }
    } else {
        emptyList()
    }
    // 滚动自动标记已读（#11）：槽位表构建与「滚过视口顶 = 已读」判定是纯函数
    // （scrollSlots/passedUnreadIds，见 FeedListSnapshot.kt），此处只做接线。
    val slotIds: List<Long?> = if (display.stickyDateHeader) {
        remember(dayGroups) { scrollSlots(articles, stickyDateHeader = true, groups = dayGroups) }
    } else {
        remember(articles) { scrollSlots(articles, stickyDateHeader = false) }
    }
    val unreadIds = remember(articles) {
        articles.filter { !it.article.isRead }.map { it.article.id }.toSet()
    }
    LaunchedEffect(listState, display.markReadOnScroll, slotIds, unreadIds, markReadPassed) {
        if (!display.markReadOnScroll) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val passed = passedUnreadIds(slotIds, firstVisible, unreadIds)
                if (passed.isNotEmpty()) markReadPassed(passed)
            }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 底部让位：tab 屏让开底部导航栏，最后一条文章能完整滚出导航栏
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        // 杂志模式：首篇大图突出（hero 跟随列表首项，翻页后仍是当前加载段的第一篇）
        val heroId = if (effective.viewMode == ListViewMode.MAGAZINE) articles.firstOrNull()?.article?.id else null
        if (display.stickyDateHeader) {
            // 粘性日期头（issue #56）：按自然日分组，吸附在顶部。
            // 只做视觉分组，不改变排序与分页；列表本身已按 publishedAt DESC 排序。
            dayGroups.forEach { group ->
                // stickyHeader 是 LazyListScope 接口成员（foundation 1.10+，真值表已核），
                // DSL 内直接调用，不可 import
                stickyHeader(key = "date-${group.key}") {
                    StickyDateHeader(group.label)
                }
                items(group.items, key = { it.article.id }) { item ->
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = null,
                            fadeOutSpec = removeFadeSpec,
                        ),
                    ) {
                        ArticleListItem(
                            item = item,
                            display = effective,
                            hero = item.article.id == heroId,
                            onArticleClick = onArticleClick,
                            onToggleRead = onToggleRead,
                            onToggleStarred = onToggleStarred,
                            onToggleBookmarked = onToggleBookmarked,
                            onDelete = onDelete,
                            onReduceSuch = onReduceSuch,
                        )
                    }
                }
            }
        } else {
            items(articles, key = { it.article.id }) { item ->
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        placementSpec = null,
                        fadeOutSpec = removeFadeSpec,
                    ),
                ) {
                    ArticleListItem(
                        item = item,
                        display = effective,
                        hero = item.article.id == heroId,
                        onArticleClick = onArticleClick,
                        onToggleRead = onToggleRead,
                        onToggleStarred = onToggleStarred,
                        onToggleBookmarked = onToggleBookmarked,
                        onDelete = onDelete,
                        onReduceSuch = onReduceSuch,
                    )
                }
            }
        }
        }
        // 滚动位置指示条：叠加在右缘，不参与布局与手势
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(6.dp)
                .articleScrollbar(listState, radarColors().textTertiary, totalCount),
        )
    }
}

/** 距列表尾部还剩这么多项时预加载下一页。 */
internal const val LOAD_MORE_THRESHOLD = 5
