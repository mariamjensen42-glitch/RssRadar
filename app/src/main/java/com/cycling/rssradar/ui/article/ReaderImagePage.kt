package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.cycling.rssradar.core.ui.theme.LocalReducedMotion
import com.cycling.rssradar.core.ui.theme.crossfadeMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

/** 捏合放大的上限。 */
private const val MAX_SCALE = 5f

/** 双击放大到的倍率（不按点击位置做偏移，放大后居中，够用且不会算错）。 */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 正文图片全屏查看页（ReadYou 差距表第 19 项）。
 *
 * 独立 Dialog 承载：`usePlatformDefaultWidth=false` + `decorFitsSystemWindows=false`
 * 让它真正铺满屏幕（盖住状态栏与导航栏），返回键由 Dialog 自动消费为关闭。
 * 不进导航路由：它是阅读页之上的瞬时 UI，进程重建时丢掉即可，不必占用 back 栈。
 *
 * 交互：左右翻页（多图）/ 捏合缩放 / 双击放大复位 / 单击关闭。
 * 缩放与翻页互斥——放大状态下 [HorizontalPager] 的滚动被关掉，避免拖图变成翻页。
 *
 * 图片仍是 Coil 懒加载，一次只有当前页（外加 pager 缓存的相邻页）在内存里，
 * 不会重演 ADR-0007 那种"整页 WebView 同时解码所有图片"的 OOM。
 */
@Composable
fun ReaderImagePage(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val count = images.size
            val pagerState = rememberPagerState(
                initialPage = initialIndex.coerceIn(0, (count - 1).coerceAtLeast(0)),
            ) { count }
            // 任一页进入放大态就锁掉翻页；翻页后旧页被 dispose，状态自然复位。
            var zoomed by remember { mutableStateOf(false) }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    url = images[page],
                    onTap = onDismiss,
                    onZoomedChange = { zoomed = it },
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Lucide.X, contentDescription = "关闭")
                }
                if (count > 1) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${pagerState.currentPage + 1} / $count",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * 单张可缩放图片：transformable 负责捏合与拖动，detectTapGestures 负责单击退出与双击缩放，
 * 位移与缩放经 graphicsLayer 落到绘制层（不重新布局，拖动跟手）。
 *
 * 未放大时 [canPan] 返回 false，拖动直接不消费，手势全部留给 [HorizontalPager] 翻页。
 */
@Composable
private fun ZoomableImage(
    url: String,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 新签名 4 参（centroid 在前）：缩放围绕手势中心（比旧版围绕原点更自然）；当前实现只用 pan
    val state = rememberTransformableState { _, zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        offset = if (next > 1f) offset + panChange else Offset.Zero
        scale = next
    }
    LaunchedEffect(scale) { onZoomedChange(scale > 1f) }

    var loading by remember(url) { mutableStateOf(true) }
    // 显式解码尺寸：全屏 Dialog 里只靠布局约束降采样不可靠，Coil 可能按原图解码出
    // 100MB+ 的 bitmap 直接撞 Canvas 上限崩溃。按屏幕 2 倍解码兼顾捏合放大清晰度，
    // clampDecodeSize 的像素预算封顶（放大超过约 2× 后清晰度渐降，可接受）。
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val reducedMotion = LocalReducedMotion.current
    val decodeWidthPx = with(LocalDensity.current) { (config.screenWidthDp * 2).dp.roundToPx() }
    val decodeHeightPx = with(LocalDensity.current) { (config.screenHeightDp * 2).dp.roundToPx() }
    val model = remember(url, decodeWidthPx, decodeHeightPx, reducedMotion) {
        ImageRequest.Builder(context)
            .data(url)
            .size(clampDecodeSize(decodeWidthPx, decodeHeightPx))
            // crossfade 200ms（docs/motion.md #3）；reduce-motion 关掉渐显
            .crossfadeMotion(reducedMotion)
            .build()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .transformable(state = state, canPan = { scale > 1f })
            .pointerInput(onTap) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        offset = Offset.Zero
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onState = { loading = it is AsyncImagePainter.State.Loading },
        )
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}
