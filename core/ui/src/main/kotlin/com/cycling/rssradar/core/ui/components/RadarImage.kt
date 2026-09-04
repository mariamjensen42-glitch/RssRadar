package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 统一图片组件：包 coil AsyncImage，提供全应用一致的加载行为——
 * crossfade 渐显；url 为空 / 加载中 / 失败时统一 surface1 底色，
 * 空 url 或失败再叠加 [fallback] 兜底图标（线性描边风格），
 * 内存 + 磁盘缓存走 coil 默认。
 *
 * 需要自定义解码尺寸 / 监听加载状态的复杂场景（如阅读页长图、图片查看器的
 * zoom 加载态）请直接用 coil AsyncImage——那类调用点本组件不覆盖。
 */
@Composable
fun RadarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: ImageVector? = null,
    fallbackSize: Dp = 18.dp,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    Box(
        modifier = modifier.background(radarColors().surface1),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank() || failed) {
            if (fallback != null) {
                Icon(
                    imageVector = fallback,
                    contentDescription = null,
                    tint = radarColors().textTertiary,
                    modifier = Modifier.size(fallbackSize),
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onState = { failed = it is coil3.compose.AsyncImagePainter.State.Error },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
