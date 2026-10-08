package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minimize2
import com.composables.icons.lucide.X
import com.cycling.rssradar.core.data.platform.openUrl
import com.cycling.rssradar.core.ui.R as UiR

/**
 * 全屏视频播放页。
 *
 * 独立 Dialog 承载：`usePlatformDefaultWidth = false` + `decorFitsSystemWindows = false`
 * 让它真正铺满屏幕，返回键由 Dialog 自动消费为关闭——与全屏看图
 * [ReaderImagePage] 同一套承载方式，也不进导航路由（阅读页之上的瞬时 UI）。
 *
 * 两条入口共用它：正文视频卡的"全屏"按钮，与底栏的视频入口（订阅源级 enclosure 视频）。
 * 关闭动作由调用方处理（停播 + 清请求），这里只管画面与控制条。
 */
@Composable
internal fun ReaderVideoPage(
    url: String,
    title: String?,
    media: ArticleMediaPlayer,
    /**
     * 退出后正文里还有这条媒体的画面（= 只是"退出全屏"，继续播）。
     * 决定左上角那颗按钮的语义与长相：`Minimize2` + 「退出全屏」 vs `X` + 「关闭」——
     * 长得像"关闭"、按下去却继续播的按钮是在骗人。
     */
    resumeInline: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 打开即播；[ArticleMediaPlayer.play] 对"已在播的同一条"不重头开始，重复进入无害
    LaunchedEffect(url) { media.play(url, MediaNodeKind.VIDEO) }

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
            val player = media.player
            if (player != null) {
                // 画面按视频比例贴合、居中留黑边。直接把画面拉满整屏会把横屏视频拉成"竖屏"
                // 且纵向变形——PlayerSurface 不做宽高比适配，见 fittedMediaModifier。
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerSurface(
                        player,
                        fittedMediaModifier(maxWidth, maxHeight, media.aspectRatio),
                        // 这里同样用 TextureView：Dialog 是独立 window，SurfaceView 的
                        // z 序与控制条叠层的配合在各 OEM 上并不一致，纹理版行为统一。
                        SURFACE_TYPE_TEXTURE_VIEW,
                    )
                }
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
                    Icon(
                        imageVector = if (resumeInline) Lucide.Minimize2 else Lucide.X,
                        contentDescription = if (resumeInline) {
                            stringResource(R.string.media_exit_fullscreen)
                        } else {
                            stringResource(UiR.string.close)
                        },
                    )
                }
                title?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            MediaControlBar(
                media = media,
                url = url,
                onLinkClick = { context.openUrl(it) },
                dark = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
