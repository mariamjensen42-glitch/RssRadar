package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.ui.theme.ACCENT_PRESETS
import com.cycling.rssradar.core.ui.theme.DEFAULT_ACCENT_ARGB
import com.cycling.rssradar.core.ui.theme.argbToHsl
import com.cycling.rssradar.core.ui.theme.onAccentFor
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.toArgb
import com.cycling.rssradar.core.ui.components.SyncedSlider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * 强调色选择（#29）：预设色板 + HSL 三滑杆。
 *
 * 只换强调色，卡片与背景不动——整套换色等于把产品视觉身份交出去。
 */
@Composable
internal fun AccentPicker(
    customAccent: Long?,
    onSelect: (Long?) -> Unit,
) {
    Text(
        text = stringResource(R.string.accent_color),
        color = radarColors().textPrimary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        text = stringResource(R.string.accent_desc),
        color = radarColors().textTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )
    // 第一枚是默认紫：选中它 = 回到默认，不是「自定义了一个紫」
    ACCENT_PRESETS.chunked(6).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            row.forEach { argb ->
                val isDefault = argb == DEFAULT_ACCENT_ARGB
                val selected = if (isDefault) customAccent == null else customAccent == argb
                AccentSwatch(
                    argb = argb,
                    selected = selected,
                    onClick = { onSelect(if (isDefault) null else argb) },
                )
            }
        }
    }
    AccentSliders(base = customAccent ?: DEFAULT_ACCENT_ARGB, onChange = { onSelect(it) })
}

@Composable
private fun AccentSwatch(argb: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(argb))
            .then(
                if (selected) {
                    Modifier.border(2.dp, radarColors().textPrimary, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    )
}

/** HSL 三滑杆 + 实时预览：拖「色相」不会顺手把颜色拖灰，比 RGB 直观。 */
@Composable
private fun AccentSliders(base: Long, onChange: (Long) -> Unit) {
    var hsl by remember(base) { mutableStateOf(argbToHsl(base)) }
    val argb = hsl.toArgb()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AccentSlider(stringResource(R.string.hue), hsl.hue, 0f..360f) { hsl = hsl.copy(hue = it); onChange(hsl.toArgb()) }
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(50), color = Color(argb)) {
            Text(
                text = stringResource(R.string.preview_aa),
                color = onAccentFor(Color(argb)),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    AccentSlider(stringResource(R.string.saturation), hsl.saturation, 0f..1f) { hsl = hsl.copy(saturation = it); onChange(hsl.toArgb()) }
    AccentSlider(stringResource(R.string.lightness), hsl.lightness, 0f..1f) { hsl = hsl.copy(lightness = it); onChange(hsl.toArgb()) }
}

@Composable
private fun AccentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = radarColors().textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp),
        )
        SyncedSlider(value = value, onValueChange = onValue, valueRange = range, modifier = Modifier.weight(1f))
    }
}
