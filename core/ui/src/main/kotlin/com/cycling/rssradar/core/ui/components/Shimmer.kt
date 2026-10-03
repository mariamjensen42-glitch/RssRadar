package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 加载中占位：M3 Expressive 的 [ContainedLoadingIndicator]（官方组件，勿再引第三方 shimmer）。
 * 调用方决定占位尺寸与裁剪；reduce-motion 场景请自行不启用（动画红线）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShimmerOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.fillMaxSize(),
            containerColor = radarColors().surface1,
            indicatorColor = radarColors().textSecondary,
        )
    }
}
