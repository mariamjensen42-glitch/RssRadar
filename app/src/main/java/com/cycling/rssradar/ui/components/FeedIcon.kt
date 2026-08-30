package com.cycling.rssradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.cycling.rssradar.ui.theme.Surface3
import com.cycling.rssradar.ui.theme.TextTertiary


/**
 * 订阅源 / 站点图标：有 [iconUrl] 时用 Coil 加载真图（底下字母块常驻打底，
 * 加载中 / 失败自然露出字母块，无需额外状态）；无图时用 title 的稳定 hash
 * 在 12 色调色板中取色，居中显示首个字符。
 */
@Composable
fun FeedIcon(
    title: String,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    size: Dp = 28.dp,
    cornerRadius: Dp = 7.dp,
) {
    val bg = remember(title) { colorForTitle(title) }
    val letter = title.trim().firstOrNull()?.toString()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.45f).sp,
            style = MaterialTheme.typography.labelLarge,
        )
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 默认无图标时的灰色方块。 */
@Composable
fun FeedIconPlaceholder(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    cornerRadius: Dp = 7.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Surface3),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
    }
}

/** 12 色调色板。 */
private val TitlePalette = listOf(
    Color(0xFF6B7CFF),
    Color(0xFF1AC6A5),
    Color(0xFFFF8A4C),
    Color(0xFFE36BA8),
    Color(0xFF5B8DEF),
    Color(0xFFFFB547),
    Color(0xFF22C55E),
    Color(0xFFAB5CFA),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF97316),
    Color(0xFF38BDF8),
)

private fun colorForTitle(title: String): Color {
    if (title.isBlank()) return Surface3
    val idx = (title.hashCode().toLong() and 0x7FFF_FFFFL) % TitlePalette.size
    return TitlePalette[idx.toInt()]
}
