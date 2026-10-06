package com.cycling.rssradar.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 加载中占位：整块区域铺底色 + 居中一枚官方 [ContainedLoadingIndicator]。
 *
 * 指示器必须给**固定尺寸**：该组件的容器尺寸是跟着 modifier 走的，早先这里是
 * `fillMaxSize()`，遇上阅读页那种 220dp 高的图片占位区就把加载动画撑成整块那么大。
 * 占位底色改由外层 Box 铺，指示器只当视觉锚点，与占位区域大小解耦。
 * 调用方决定占位区域尺寸与裁剪；reduce-motion 场景请自行不启用（动画红线）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShimmerOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(radarColors().surface1),
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(
                width = LoadingIndicatorDefaults.ContainerWidth,
                height = LoadingIndicatorDefaults.ContainerHeight,
            ),
            containerColor = radarColors().surface1,
            indicatorColor = radarColors().textSecondary,
        )
    }
}
