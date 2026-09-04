package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 统一图片组件：包 coil AsyncImage，提供全应用一致的加载行为——
 * crossfade 渐显、加载中 / url 为空 / 失败时统一 surface1 底色 + [fallback] 兜底图标
 * （线性描边风格），内存 + 磁盘缓存走 coil 默认。
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
    Box(
        modifier = modifier.background(radarColors().surface1),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
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
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
