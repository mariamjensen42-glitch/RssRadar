package com.cycling.rssradar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.cycling.rssradar.core.ui.components.EmptyState
import com.cycling.rssradar.core.ui.components.OptionRow
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * core:ui 通用组件的语义树测试。
 *
 * 这里刻意只用组件自己的 String 参数（不碰资源文案），断言因此不受 i18n 影响；
 * 文案走资源的那部分由 Screen 级测试覆盖。
 */
@RunWith(AndroidJUnit4::class)
class CoreUiComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingSwitchRow_showsLabelAndSubtitle() {
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                SettingSwitchRow(
                    label = "仅 WiFi 下同步",
                    checked = true,
                    onChange = {},
                    subtitle = "不消耗移动数据",
                )
            }
        }
        compose.onNodeWithText("仅 WiFi 下同步").assertIsDisplayed()
        compose.onNodeWithText("不消耗移动数据").assertIsDisplayed()
    }

    @Test
    fun settingSwitchRow_toggleReportsNewValue() {
        var received: Boolean? = null
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                SettingSwitchRow(
                    label = "自动同步",
                    checked = false,
                    onChange = { received = it },
                )
            }
        }
        compose.onNode(isToggleable()).performClick()
        assertEquals(true, received)
    }

    /** UI 铁律：禁用必须配解释文案——禁用态的说明文字不能消失。 */
    @Test
    fun settingSwitchRow_disabledStillShowsReason() {
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                SettingSwitchRow(
                    label = "动态取色",
                    checked = false,
                    onChange = {},
                    enabled = false,
                    subtitle = "系统版本低于 Android 12，不可用",
                )
            }
        }
        compose.onNodeWithText("系统版本低于 Android 12，不可用").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsMessageAndHint() {
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                EmptyState(
                    icon = Lucide.Rss,
                    message = "还没有订阅源",
                    hint = "从 RSSHub 目录挑一个开始",
                )
            }
        }
        compose.onNodeWithText("还没有订阅源").assertIsDisplayed()
        compose.onNodeWithText("从 RSSHub 目录挑一个开始").assertIsDisplayed()
    }

    @Test
    fun optionRow_clickFiresCallback() {
        var clicked = false
        compose.setContent {
            RssRadarTheme(darkTheme = false) {
                OptionRow(
                    label = "打开链接",
                    value = "应用内",
                    onClick = { clicked = true },
                )
            }
        }
        compose.onNodeWithText("打开链接").performClick()
        assertTrue(clicked)
    }
}
