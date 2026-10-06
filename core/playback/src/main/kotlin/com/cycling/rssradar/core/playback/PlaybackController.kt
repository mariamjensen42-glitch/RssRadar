package com.cycling.rssradar.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 队列里的一条音频：播放所需的全部信息，不依赖 UI 层类型。 */
data class PlaybackTrack(
    val articleId: Long,
    val url: String,
    val title: String,
    val feedTitle: String,
    val artworkUrl: String?,
)

data class PlaybackState(
    val connected: Boolean = false,
    val articleId: Long? = null,
    val title: String? = null,
    val feedTitle: String? = null,
    val artworkUrl: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    /** 播放器报的错误原文（网络断了、格式不支持等），UI 层如实显示。 */
    val error: String? = null,
)

/**
 * 播放控制的唯一入口：连 [PlaybackService] 的 MediaSession，把播放器状态收成一条 StateFlow。
 *
 * 用 MediaController 而不是自己持有 ExoPlayer：播放器活在服务里，界面只是个观察者，
 * 退出播放页/旋转屏幕都不会打断播放，通知栏与锁屏的控制也自动与之同步。
 *
 * 连接是**惰性且幂等**的：没人点播放就不连服务，服务也就不会起来占内存。
 */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var building = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var pending: (() -> Unit)? = null

    /** 当前想要的速度：控制器就绪前设的也记住，接上后一次性套用。 */
    private var speed = 1f

    fun ensureConnected(onReady: () -> Unit = {}) {
        val existing = controller
        if (existing != null) {
            onReady()
            return
        }
        // 连接中再调用：把回调排进队列，别开第二条连接
        if (building) {
            pending = onReady
            return
        }
        building = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        // 用平台主线程执行器而不是 guava 的 MoreExecutors：少一个直接依赖，
        // 行为一致（都是把回调投到主线程）
        val executor: Executor = ContextCompat.getMainExecutor(context)
        future.addListener(
            {
                building = false
                val built = runCatching { future.get() }.getOrNull()
                if (built == null) {
                    _state.value = _state.value.copy(error = "PLAYBACK_CONNECT_FAILED")
                    return@addListener
                }
                controller = built
                observe(built)
                onReady()
                pending?.invoke()
                pending = null
            },
            executor,
        )
    }

    fun playQueue(queue: List<PlaybackTrack>, startIndex: Int) {
        if (queue.isEmpty()) return
        ensureConnected {
            val c = controller ?: return@ensureConnected
            c.setMediaItems(queue.map { it.toMediaItem() }, startIndex.coerceIn(0, queue.lastIndex), C.TIME_UNSET)
            c.setPlaybackSpeed(speed)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        ensureConnected {
            val c = controller ?: return@ensureConnected
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** ±15 秒（播客的常规跳转粒度，UI 上给的就是这两个按钮）。 */
    fun skipBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = c.duration
        c.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun setSpeed(value: Float) {
        speed = value
        controller?.setPlaybackSpeed(value)
        _state.value = _state.value.copy(speed = value)
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun pause() {
        controller?.pause()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun observe(c: MediaController) {
        c.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(error = error.message ?: error.errorCodeName)
                    publish()
                }
            },
        )
        startTicker()
        publish()
    }

    /**
     * 进度靠轮询而不是等回调：ExoPlayer 没有"位置变了"的推送，
     * 而进度条与时间显示都需要它。500ms 足够顺，代价可以忽略。
     */
    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) publish()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = _state.value.copy(connected = false)
            return
        }
        val metadata = c.mediaMetadata
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        _state.value = PlaybackState(
            connected = true,
            articleId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            title = metadata.title?.toString(),
            feedTitle = metadata.artist?.toString(),
            artworkUrl = metadata.artworkUri?.toString(),
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            speed = c.playbackParameters.speed,
            error = _state.value.error,
        )
    }

    private fun PlaybackTrack.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setMediaId(articleId.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(feedTitle)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .build(),
        )
        .build()

    private companion object {
        const val TICK_MILLIS = 500L
    }
}
