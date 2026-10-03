package com.cycling.rssradar.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.cycling.rssradar.R
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.components.SyncedSlider
import com.cycling.rssradar.core.ui.components.SegmentedChips
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Headphones
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward

private val SPEEDS = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)

private const val SKIP_MILLIS = 15_000L

/**
 * 音频/播客播放页：封面、进度、±15 秒、上一集/下一集、倍速。
 *
 * 播放器本体在 [com.cycling.rssradar.playback.PlaybackService] 里，本页只是它的一个视图——
 * 退出页面播放继续，通知栏与锁屏也能控制。所以这里**不发停止**，返回只是返回。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerScreen(
    articleId: Long,
    onBack: () -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playback.state.collectAsState()
    val missing by viewModel.missing.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val colors = radarColors()
    val context = LocalContext.current

    LaunchedEffect(articleId) { viewModel.open(articleId) }

    // 拖动进度条期间用本地值，松手才 seek——否则轮询会把手指按着的位置一直拽回去
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = colors.textPrimary)
            }
            Text(
                text = stringResource(R.string.player_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (queue.size > 1 && state.articleId != null) {
                val index = queue.indexOfFirst { it.articleId == state.articleId }
                if (index >= 0) {
                    Text(
                        text = stringResource(R.string.player_queue_position, index + 1, queue.size),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (missing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.player_no_audio),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                val artwork = state.artworkUrl
                if (artwork.isNullOrBlank()) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Lucide.Headphones,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artwork).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // 缓冲：M3 Expressive 的 LoadingIndicator（在封面正中央转）
            if (state.buffering) {
                LoadingIndicator()
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                text = state.title ?: stringResource(R.string.player_unknown_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.feedTitle?.let { feed ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = feed,
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val duration = state.durationMs
        val progress = scrubbing
            ?: if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SyncedSlider(
                value = progress,
                valueRange = 0f..1f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    val target = scrubbing
                    if (target != null && duration > 0) viewModel.playback.seekTo((target * duration).toLong())
                    scrubbing = null
                },
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatTime((progress * duration).toLong()),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (duration > 0) formatTime(duration) else stringResource(R.string.player_live_stream),
                    color = colors.textTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel.playback::previous, enabled = queue.size > 1) {
                Icon(Lucide.SkipBack, contentDescription = stringResource(R.string.player_previous), tint = colors.textPrimary)
            }
            IconButton(onClick = { viewModel.playback.skipBy(-SKIP_MILLIS) }) {
                Icon(Lucide.RotateCcw, contentDescription = stringResource(R.string.player_back_15), tint = colors.textPrimary)
            }
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = CircleShape,
                color = colors.accent,
                modifier = Modifier.size(64.dp),
            ) {
                IconButton(onClick = viewModel.playback::togglePlayPause) {
                    Icon(
                        if (state.playing) Lucide.Pause else Lucide.Play,
                        contentDescription = stringResource(
                            if (state.playing) R.string.player_pause else R.string.player_play,
                        ),
                        tint = colors.onAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = { viewModel.playback.skipBy(SKIP_MILLIS) }) {
                Icon(Lucide.RotateCw, contentDescription = stringResource(R.string.player_forward_15), tint = colors.textPrimary)
            }
            IconButton(onClick = viewModel.playback::next, enabled = queue.size > 1) {
                Icon(Lucide.SkipForward, contentDescription = stringResource(R.string.player_next), tint = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(16.dp))
        val speedLabels = SPEEDS.associateWith { speed -> formatSpeed(speed) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SegmentedChips(
                options = SPEEDS,
                selected = SPEEDS.minByOrNull { kotlin.math.abs(it - state.speed) } ?: 1.0f,
                label = { speed -> speedLabels[speed].orEmpty() },
                onSelect = viewModel.playback::setSpeed,
            )
        }

        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.player_error, message),
                color = colors.accent,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"
