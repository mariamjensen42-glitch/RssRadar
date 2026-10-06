package com.cycling.rssradar.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.core.data.repository.ReadingStatsRepository
import com.cycling.rssradar.core.domain.stats.ReadingStatsDashboard
import com.cycling.rssradar.core.ui.mvi.MviStateViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 统计页无用户事件（纯只读），Intent 保留空壳以符合 MVI 契约。 */
sealed interface ReadingStatsIntent

@HiltViewModel
class ReadingStatsViewModel @Inject constructor(
    private val repository: ReadingStatsRepository,
) : ViewModel(), MviStateViewModel<ReadingStatsIntent, ReadingStatsUiState> {

    private val _uiState = MutableStateFlow(ReadingStatsUiState())
    override val uiState: StateFlow<ReadingStatsUiState> = _uiState.asStateFlow()

    override fun onIntent(intent: ReadingStatsIntent) = Unit

    init {
        viewModelScope.launch {
            // 口径装配收敛到 ReadingStatsDashboard（core/domain 纯函数，JVM 可测）——
            // 本 VM 只负责取数：每个数字都能回溯到一条查询，装配规则只写一遍。
            val now = System.currentTimeMillis()
            val zoneOffset = TimeZone.getDefault().getOffset(now)

            val since = now - ReadingStatsDashboard.WINDOW_DAYS * ReadingStatsDashboard.DAY_MS
            val window = repository.windowStat(since)
            // 全部打开时间戳：活跃时段只要近 7 天的，streak 要全部历史（断一天就断）
            val allOpened = repository.allOpenedTimestamps()
            val perFeed = repository.openedCountsByFeedSince(since)
            val top = repository.topOpenedFeeds(since, ReadingStatsDashboard.TOP_FEED_LIMIT)

            val summary = ReadingStatsDashboard.assemble(
                ReadingStatsDashboard.Inputs(
                    now = now,
                    zoneOffsetMillis = zoneOffset,
                    windowCnt = window.cnt,
                    windowMinutes = window.minutes,
                    allOpened = allOpened,
                    openedCountsByFeed = perFeed.map { it.cnt },
                ),
            )

            _uiState.value = ReadingStatsUiState(
                weekOpens = summary.weekOpens,
                weekMinutes = summary.weekMinutes,
                activeHours = summary.activeHours,
                topFeeds = top,
                concentration = summary.concentration,
                streakDays = summary.streakDays,
                starredCount = repository.starredCount(),
                bookmarkedCount = repository.bookmarkedCount(),
                loaded = true,
            )
            // 未读存量跟随 DB 实时变化，单独 collect
            launch {
                repository.observeUnreadCount().collect { unread ->
                    _uiState.value = _uiState.value.copy(unreadCount = unread)
                }
            }
        }
    }
}
