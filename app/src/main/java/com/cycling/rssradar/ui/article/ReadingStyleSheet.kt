package com.cycling.rssradar.ui.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.store.model.ReadingFontFamily
import com.cycling.rssradar.core.data.store.model.ReadingImageState
import com.cycling.rssradar.core.data.store.model.ReadingPrefs
import com.cycling.rssradar.core.data.store.model.ReadingRenderer
import com.cycling.rssradar.core.data.store.model.ReadingStyleState
import com.cycling.rssradar.core.data.store.model.ReadingTextAlign
import com.cycling.rssradar.core.data.store.model.ReadingTheme
import com.cycling.rssradar.core.data.store.model.coerceFontSize
import com.cycling.rssradar.core.data.store.model.coerceImageCornerRadius
import com.cycling.rssradar.core.data.store.model.coerceLetterSpacing
import com.cycling.rssradar.core.data.store.model.coerceLineHeight
import com.cycling.rssradar.core.data.store.model.coercePadding
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.ui.components.SyncedSlider
import kotlin.math.roundToInt

/**
 * 排版设置弹层（issue #42）：渲染器、字号步进、行距/边距滑杆、字体族、图片圆角/放大。
 * 显示值读 [LocalReadingPrefs]，写入经 VM 直达 ReadingPrefsStore，无确认按钮即改即见。
 *
 * 整份偏好作为一个参数进出，而不是拆成「渲染器 + 图片 + 排版」若干组回调——
 * 四项同属阅读偏好，拆开只会把接线成本再复制一遍。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingStyleSheet(
    prefs: ReadingPrefs,
    onRenderer: (ReadingRenderer) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Float) -> Unit,
    onPadding: (Int) -> Unit,
    onLetterSpacing: (Float) -> Unit,
    onTextAlign: (ReadingTextAlign) -> Unit,
    onFontFamily: (ReadingFontFamily) -> Unit,
    onImageCornerRadius: (Int) -> Unit,
    onImageMaximize: (Boolean) -> Unit,
    onImmersive: (Boolean) -> Unit,
    /** 滚动时自动隐藏工具栏（ReadYou 差距表 #22）；与 [onImmersive] 是两件事。 */
    autoHideBars: Boolean = false,
    onAutoHideBars: (Boolean) -> Unit = {},
    /** 阅读主题（ReadYou 差距表 #16）：四档配色，只换背景/表面/文字。 */
    onReadingTheme: (ReadingTheme) -> Unit = {},
    /** 越界切篇（ReadYou 差距表 #23）：顶部下拉看上一篇、底部上拉看下一篇。 */
    onPullToSwitch: (Boolean) -> Unit = {},
    /** 本文能否在「正文 / 摘要」之间切（[canSwitchToSummary]）：不能切时整块不出现。 */
    canSwitchToSummary: Boolean = false,
    /** 当前是否切成摘要。 */
    preferSummary: Boolean = false,
    onPreferSummary: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val style = prefs.style
    val image = prefs.image
    val renderer = prefs.renderer
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = radarColors().surface1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.typography_settings),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            // 本文：正文 / 摘要（ReadYou 的 renderFullContent/renderDescriptionContent 同款）。
            // 只在两者实质不同时给这一块：ADR-0001 入库时取 description 与 content 的较长者，
            // 大量源的 content 就是 summary——那时给个开关，点下去屏幕纹丝不动。
            // 没有意义的按钮不该存在，所以不成立时整块不渲染。
            if (canSwitchToSummary) {
                Text(
                    text = stringResource(R.string.body_section),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to stringResource(R.string.body_full), true to stringResource(R.string.body_summary)).forEach { (isSummary, label) ->
                        val selected = isSummary == preferSummary
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) radarColors().accent else radarColors().surface2,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPreferSummary(isSummary) },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) radarColors().onAccent else radarColors().textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.summary_hint),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            // 阅读主题（ReadYou 差距表 #16）：四档。只换「纸的颜色」——背景/表面/文字，
            // 强调色仍跟随应用（含 #29 的自定义色），且不随系统深浅变化：挑「纸张」
            // 就是为了在深色模式下也要米黄纸。
            Text(
                text = stringResource(R.string.reading_theme),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingTheme.entries.forEach { theme ->
                    val selected = theme == prefs.readingTheme
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) radarColors().accent else radarColors().surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onReadingTheme(theme) },
                    ) {
                        Text(
                            text = theme.label,
                            color = if (selected) radarColors().onAccent else radarColors().textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.reading_theme_hint),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            // 正文渲染器：WebView / 原生 Compose 二选一（ADR-0009）。
            // 原生路对表格/视频/内联样式退化，仅建议被 WebView 滚动闪烁困扰时启用。
            Text(
                text = stringResource(R.string.body_renderer),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingRenderer.entries.forEach { r ->
                    val selected = r == renderer
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) radarColors().accent else radarColors().surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRenderer(r) },
                    ) {
                        Text(
                            text = r.label,
                            color = if (selected) radarColors().onAccent else radarColors().textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 字号：步进
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.font_size),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onFontSize(coerceFontSize(style.fontSize - 1)) }) {
                    Icon(Lucide.Minus, contentDescription = stringResource(R.string.font_decrease), tint = radarColors().textPrimary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${style.fontSize}",
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp),
                )
                IconButton(onClick = { onFontSize(coerceFontSize(style.fontSize + 1)) }) {
                    Icon(Lucide.Plus, contentDescription = stringResource(R.string.font_increase), tint = radarColors().textPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // 行距：滑杆（0.8–2.5）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.line_height),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                SyncedSlider(
                    value = style.lineHeight,
                    onValueChange = { onLineHeight(coerceLineHeight(it)) },
                    valueRange = ReadingStyleState.LINE_HEIGHT_MIN..ReadingStyleState.LINE_HEIGHT_MAX,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f".format(style.lineHeight),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }

            // 边距：滑杆（0–48dp）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.margin),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                SyncedSlider(
                    value = style.horizontalPadding.toFloat(),
                    onValueChange = { onPadding(coercePadding(it.roundToInt())) },
                    valueRange = ReadingStyleState.PADDING_MIN.toFloat()..ReadingStyleState.PADDING_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${style.horizontalPadding}dp",
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            // 字间距（ReadYou 差距表 #17）：中文长段落拉开一点明显好读
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.letter_spacing),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                SyncedSlider(
                    value = style.letterSpacing,
                    onValueChange = { onLetterSpacing(coerceLetterSpacing(it)) },
                    valueRange = ReadingStyleState.LETTER_SPACING_MIN..ReadingStyleState.LETTER_SPACING_MAX,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f".format(style.letterSpacing),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }

            // 正文对齐（ReadYou 差距表 #17）：只作用于没有自带 align 声明的段落，
            // 正文里写死的居中/右对齐是内容的一部分，不该被全局偏好盖掉。
            Text(
                text = stringResource(R.string.text_align),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingTextAlign.entries.forEach { align ->
                    val selected = align == style.textAlign
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) radarColors().accent else radarColors().surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTextAlign(align) },
                    ) {
                        Text(
                            text = align.label,
                            color = if (selected) radarColors().onAccent else radarColors().textSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 字体族：三选一
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingFontFamily.entries.forEach { family ->
                    val selected = family == style.fontFamily
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) radarColors().accent else radarColors().surface2,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = { onFontFamily(family) }),
                    ) {
                        Text(
                            text = family.label,
                            color = if (selected) radarColors().onAccent else radarColors().textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 图片（issue #60）：圆角直接改 CSS/Compose 形状；点击放大关掉后，
            // 正文不再把 <img> 包成链接，点图在 WebView 里自然无反应。
            Text(
                text = stringResource(R.string.images),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.corner_radius),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(72.dp),
                )
                SyncedSlider(
                    value = image.cornerRadius.toFloat(),
                    onValueChange = { onImageCornerRadius(coerceImageCornerRadius(it.roundToInt())) },
                    valueRange = ReadingImageState.CORNER_RADIUS_MIN.toFloat()..
                        ReadingImageState.CORNER_RADIUS_MAX.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${image.cornerRadius}dp",
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tap_to_zoom),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = image.maximizeOnTap,
                    onCheckedChange = onImageMaximize,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = radarColors().onAccent,
                        checkedTrackColor = radarColors().accent,
                    ),
                )
            }

            // 沉浸模式（issue #93）：只留正文与图片，剥掉分享/推荐/评论等网页杂乱元素
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.immersive_mode),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.immersive_hint),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = prefs.immersive,
                    onCheckedChange = onImmersive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = radarColors().onAccent,
                        checkedTrackColor = radarColors().accent,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))
            // 自动隐藏工具栏（ReadYou 差距表 #22）：**不是**上面那个沉浸模式——
            // 那是砍内容噪声，这只是把顶栏/底栏收起来腾阅读空间。名字必须分开，
            // 否则两个开关共用一个形容词，用户（和两周后的我们）分不清谁干啥。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_hide_bars),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.auto_hide_hint),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = autoHideBars,
                    onCheckedChange = onAutoHideBars,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = radarColors().onAccent,
                        checkedTrackColor = radarColors().accent,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))
            // 越界切篇（ReadYou 差距表 #23）。默认关：手势换掉正在读的东西是强感知改动。
            // 只做「上/下篇切换」，ReadYou 那个「下拉加载下一个 feed」不做——
            // 那会悄悄换掉你正在看的东西，是惊喜不是功能。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pull_switch),
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.pull_switch_hint),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = prefs.pullToSwitchArticle,
                    onCheckedChange = onPullToSwitch,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = radarColors().onAccent,
                        checkedTrackColor = radarColors().accent,
                    ),
                )
            }
        }
    }
}
