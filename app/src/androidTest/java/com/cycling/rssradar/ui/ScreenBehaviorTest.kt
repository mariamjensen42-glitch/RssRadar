package com.cycling.rssradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.ui.feed.FeedListScreen
import com.cycling.rssradar.ui.feed.FeedListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screen 级语义树测试。
 *
 * 这里能直接喂模拟状态、不启动 Hilt、也不碰 ViewModel——因为 Screen 已经拆成纯 UI
 * （只收 state + 回调 + 插槽）。文案走资源，所以断言用 `R.string` 取值而不是写死字符串。
 */
@RunWith(AndroidJUnit4::class)
class ScreenBehaviorTest {

    @get:Rule
    val compose = createComposeRule()

    private val emptyCopy: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.cycling.rssradar.ui.feed.R.string.feed_empty_no_feeds)

    /**
     * 首屏查询在途（`isFirstLoad = true`）时不渲染空态。
     * 否则空库查询期间会先闪一屏「还没有订阅」再被真实列表覆盖。
     */
    @Test
    fun feedList_firstLoadHidesEmptyState() {
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                FeedListScreen(uiState = FeedListUiState())
            }
        }
        compose.onNodeWithText(emptyCopy).assertDoesNotExist()
    }

    /** 首屏已落过一次且仍然没有数据，才显示空态。 */
    @Test
    fun feedList_emptyAfterFirstLoadShowsEmptyState() {
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                FeedListScreen(uiState = FeedListUiState(isFirstLoad = false))
            }
        }
        compose.onNodeWithText(emptyCopy).assertIsDisplayed()
    }

    /** 深色主题下同样渲染（主题只换配色，不改结构）。 */
    @Test
    fun feedList_darkThemeRendersSameState() {
        compose.setContent {
            RssRadarTheme(darkTheme = true) {
                FeedListScreen(uiState = FeedListUiState(isFirstLoad = false))
            }
        }
        compose.onNodeWithText(emptyCopy).assertIsDisplayed()
    }
}
