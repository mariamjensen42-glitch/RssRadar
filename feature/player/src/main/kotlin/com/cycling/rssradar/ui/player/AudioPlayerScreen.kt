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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.rssradar.core.playback.PlaybackState
import com.cycling.rssradar.core.playback.PlaybackTrack
import com.cycling.rssradar.core.ui.text.formatMediaTime
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
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.SegmentedChips
import com.cycling.rssradar.core.ui.components.SyncedSlider
import com.cycling.rssradar.ui.player.R
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
 * 播放器本体在 [com.cycling.rssradar.core.playback.PlaybackService] 里，本页只是它的一个视图——
 * 退出页面播放继续，通知栏与锁屏也能控制。所以这里**不发停止**，返回只是返回。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerDestination(
    articleId: Long,
    onBack: () -> Unit,
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(articleId) { viewModel.open(articleId) }
    AudioPlayerScreen(
        state = uiState.playback,
        missing = uiState.missing,
        queue = uiState.queue,
        onBack = onBack,
        onSeek = viewModel.playback::seekTo,
        onPrevious = viewModel.playback::previous,
        onSkipBy = viewModel.playback::skipBy,
        onTogglePlayPause = viewModel.playback::togglePlayPause,
        onNext = viewModel.playback::next,
        onSetSpeed = viewModel.playback::setSpeed,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioPlayerScreen(
    state: PlaybackState,
    missing: Boolean,
    queue: List<PlaybackTrack>,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit = {},
    onPrevious: () -> Unit = {},
    onSkipBy: (Long) -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    // 拖动进度条期间用本地值，松手才 seek——否则轮询会把手指按着的位置一直拽回去
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(UiR.string.back), tint = colors.onSurface)
            }
            Text(
                text = stringResource(R.string.player_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (queue.size > 1 && state.articleId != null) {
                val index = queue.indexOfFirst { it.articleId == state.articleId }
                if (index >= 0) {
                    Text(
                        text = stringResource(R.string.player_queue_position, index + 1, queue.size),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (missing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.player_no_audio),
                    color = colors.onSurfaceVariant,
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
                color = colors.surfaceContainerLowest,
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
                            tint = colors.onSurfaceVariant,
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
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.feedTitle?.let { feed ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = feed,
                    color = colors.onSurfaceVariant,
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
                    if (target != null && duration > 0) onSeek((target * duration).toLong())
                    scrubbing = null
                },
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatMediaTime((progress * duration).toLong()),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (duration > 0) formatMediaTime(duration) else stringResource(R.string.player_live_stream),
                    color = colors.onSurfaceVariant,
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
            IconButton(onClick = onPrevious, enabled = queue.size > 1) {
                Icon(Lucide.SkipBack, contentDescription = stringResource(R.string.player_previous), tint = colors.onSurface)
            }
            IconButton(onClick = { onSkipBy(-SKIP_MILLIS) }) {
                Icon(Lucide.RotateCcw, contentDescription = stringResource(R.string.player_back_15), tint = colors.onSurface)
            }
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = CircleShape,
                color = colors.primary,
                modifier = Modifier.size(64.dp),
            ) {
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (state.playing) Lucide.Pause else Lucide.Play,
                        contentDescription = stringResource(
                            if (state.playing) R.string.player_pause else R.string.player_play,
                        ),
                        tint = colors.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = { onSkipBy(SKIP_MILLIS) }) {
                Icon(Lucide.RotateCw, contentDescription = stringResource(R.string.player_forward_15), tint = colors.onSurface)
            }
            IconButton(onClick = onNext, enabled = queue.size > 1) {
                Icon(Lucide.SkipForward, contentDescription = stringResource(R.string.player_next), tint = colors.onSurface)
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
                onSelect = onSetSpeed,
            )
        }

        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.player_error, message),
                color = colors.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "${speed}×"
