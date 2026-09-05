package com.cycling.rssradar.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.data.db.ArticleDao
import com.cycling.rssradar.core.data.db.FeedOpenStat
import com.cycling.rssradar.core.domain.stats.ReadingStatsDashboard
import com.cycling.rssradar.core.domain.stats.TopFeed
import com.cycling.rssradar.core.ui.theme.radarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val WEEK_MS = 7 * DAY_MS

/** 统计仪表盘 UiState（#83）：所有数字来自 DB 真实计算，一个都不许编。 */
data class ReadingStatsUiState(
    /** 近 7 天打开篇数（口径 lastOpenedAt，滑动标已读不算）。 */
    val weekOpens: Int = 0,
    /** 近 7 天估算阅读分钟合计（readingMinutes 求和，UI 必须标注「估算」）。 */
    val weekMinutes: Long = 0,
    /** 活跃时段（AiReadingStats.activeHours，近 7 天样本）。 */
    val activeHours: List<Int> = emptyList(),
    /** Top 5 打开源（近 7 天）。 */
    val topFeeds: List<FeedOpenStat> = emptyList(),
    /** 源集中度（归一化 HHI，0~1，近 7 天全部打开样本）。 */
    val concentration: Double = 0.0,
    /** 连续阅读天数（今天未打开则从昨天起数）。 */
    val streakDays: Int = 0,
    val starredCount: Int = 0,
    val bookmarkedCount: Int = 0,
    val unreadCount: Int = 0,
    val loaded: Boolean = false,
)

/**
 * 统计仪表盘 VM（#83）：编排 SQL 原料与 [AiReadingStats] 纯函数。
 * 纯函数管算法，DAO 管取数，这里只拼装——保证每个数字都能回溯到一条查询。
 */
@HiltViewModel
class ReadingStatsViewModel @Inject constructor(
    private val articleDao: ArticleDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ReadingStatsUiState())
    val state: StateFlow<ReadingStatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // 口径装配收敛到 ReadingStatsDashboard（core/domain 纯函数，JVM 可测）——
            // 本 VM 只负责取数：每个数字都能回溯到一条查询，装配规则只写一遍。
            val now = System.currentTimeMillis()
            val zoneOffset = java.util.TimeZone.getDefault().getOffset(now)

            val window = articleDao.readingWindowStat(now - WEEK_MS)
            // 全部打开时间戳：活跃时段只要近 7 天的，streak 要全部历史（断一天就断）
            val allOpened = articleDao.allOpenedTimestamps()
            val perFeed = articleDao.openedCountsByFeedSince(now - WEEK_MS)
            val top = articleDao.topOpenedFeeds(now - WEEK_MS, TOP_FEED_LIMIT)

            val summary = ReadingStatsDashboard.assemble(
                ReadingStatsDashboard.Inputs(
                    now = now,
                    zoneOffsetMillis = zoneOffset,
                    windowCnt = window.cnt,
                    windowMinutes = window.minutes,
                    allOpened = allOpened,
                    openedCountsByFeed = perFeed.map { it.cnt },
                    topFeeds = top.map { TopFeed(it.feedTitle, it.cnt) },
                ),
            )

            _state.value = ReadingStatsUiState(
                weekOpens = summary.weekOpens,
                weekMinutes = summary.weekMinutes,
                activeHours = summary.activeHours,
                topFeeds = top,
                concentration = summary.concentration,
                streakDays = summary.streakDays,
                starredCount = articleDao.starredCount(),
                bookmarkedCount = articleDao.bookmarkedCount(),
                loaded = true,
            )
            // 未读存量跟随 DB 实时变化，单独 collect
            launch {
                articleDao.observeUnreadCount().collect { unread ->
                    _state.value = _state.value.copy(unreadCount = unread)
                }
            }
        }
    }

    companion object {
        const val TOP_FEED_LIMIT = 5
    }
}

/** 统计仪表盘页（#83）：一屏卡片，近 7 天滚动窗，无切换。 */
@Composable
fun ReadingStatsScreen(
    onBack: () -> Unit,
    viewModel: ReadingStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = radarColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = colors.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "阅读统计",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "近 7 天 · 口径为真实打开文章，滑动标已读不计入",
            color = colors.textTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(16.dp))

        if (!state.loaded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
            return@Column
        }

        // —— 窗口总量 ——
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatsBigCard(
                value = state.weekOpens.toString(),
                label = "近 7 天打开",
                modifier = Modifier.weight(1f),
            )
            StatsBigCard(
                // 「估算」如实标注：readingMinutes 是按字数估的，不是真实停留计时（CONTEXT.md）
                value = formatMinutes(state.weekMinutes),
                label = "阅读分钟（估算）",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatsBigCard(value = state.streakDays.toString(), label = "连续阅读天数", modifier = Modifier.weight(1f))
            StatsBigCard(value = state.unreadCount.toString(), label = "当前未读", modifier = Modifier.weight(1f))
            StatsBigCard(value = (state.starredCount + state.bookmarkedCount).toString(), label = "收藏/稍后读", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // —— 活跃时段 ——
        StatsSectionCard(title = "活跃时段") {
            if (state.activeHours.isEmpty()) {
                StatsEmpty("样本不足——近 7 天打开太少，看不出习惯")
            } else {
                Text(
                    text = state.activeHours.joinToString("、") { "$it 点" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "明显高于全天平均的打开时段",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // —— Top 5 + 集中度 ——
        StatsSectionCard(title = "最常打开的订阅源") {
            if (state.topFeeds.isEmpty()) {
                StatsEmpty("近 7 天还没有打开记录")
            } else {
                state.topFeeds.forEachIndexed { index, feed ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = colors.textTertiary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            text = feed.feedTitle,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            text = "${feed.cnt} 篇",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val pct = (state.concentration * 100).toInt()
                Text(
                    text = "源集中度 $pct%——${
                        when {
                            pct >= 60 -> "阅读集中在少数源"
                            pct >= 30 -> "分布适中"
                            else -> "阅读相当分散"
                        }
                    }",
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsBigCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatsSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                color = radarColors().textSecondary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun StatsEmpty(text: String) {
    Text(text = text, color = radarColors().textTertiary, style = MaterialTheme.typography.bodyMedium)
}

/** 分钟数转人话：60 分钟以下显示「N 分钟」，以上折算小时（估值，别装精确）。 */
private fun formatMinutes(minutes: Long): String =
    if (minutes < 60) "$minutes" else "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}"
