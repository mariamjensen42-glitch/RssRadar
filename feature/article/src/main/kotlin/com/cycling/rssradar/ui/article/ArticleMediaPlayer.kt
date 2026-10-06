package com.cycling.rssradar.ui.article

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.cycling.rssradar.core.ui.components.SyncedSlider
import com.cycling.rssradar.core.ui.theme.radarColors
import java.util.Locale
import kotlinx.coroutines.delay

/** 进度采样间隔：ExoPlayer 不推送位置变化，进度只能轮询（与 PlaybackController 同策略，这里更密）。 */
private const val MEDIA_TICK_MILLIS = 250L

private const val MEDIA_CORNER_DP = 12

private const val VIDEO_LOADING_SIZE_DP = 56
private const val MEDIA_BUTTON_SIZE_DP = 40
private const val MEDIA_BUTTON_SMALL_SIZE_DP = 36

/**
 * 贴合规则落到布局上的唯一入口：可用框尺寸不确定时退化成铺满（见 [fitMediaSize]）。
 */
internal fun fittedMediaModifier(width: Dp, height: Dp, aspectRatio: Float): Modifier {
    val w = width.value
    val h = height.value
    if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) return Modifier.fillMaxSize()
    return Modifier.size(fitMediaSize(width, height, aspectRatio))
}

/**
 * 正文媒体的播放器（ADR-0018）。
 *
 * **一条正文只有一个实例**——整页扫描下来最坏会有几十个媒体节点，人手一个 ExoPlayer
 * 就是几十套解码器与音频通道。这里只维护"当前在播的那一条"：切换媒体复用同一个播放器，
 * 实例本身**延迟创建**（没人点播放就不建，不点开视频的文章一个字节的解码资源都不占）。
 *
 * 为什么不复用 [com.cycling.rssradar.core.playback.PlaybackController]：那条链是
 * **后台播客**语义（MediaSession + 前台服务 + 通知栏，退出页面继续响）。正文里的媒体是
 * "页面内的一次性播放"，退到后台就该停——两者生命周期相反，共用一套只会互相打架。
 *
 * 页面级宿主见 [rememberArticleMediaPlayer]；正文渲染层经 [LocalArticleMediaPlayer] 取用。
 */
internal class ArticleMediaPlayer(private val context: Context) {

    private var exo: ExoPlayer? = null

    /** 当前在播媒体的地址；null = 没有媒体处于播放态（不是"暂停"）。 */
    var activeUrl by mutableStateOf<String?>(null)
        private set

    var activeKind by mutableStateOf(MediaNodeKind.EMBED)
        private set

    var playing by mutableStateOf(false)
        private set

    var buffering by mutableStateOf(false)
        private set

    var positionMs by mutableStateOf(0L)
        private set

    /** 总时长；0 = 未知（直播流、尚未 prepare 完）。 */
    var durationMs by mutableStateOf(0L)
        private set

    var aspectRatio by mutableStateOf(MEDIA_PLACEHOLDER_ASPECT)
        private set

    /** 播放器报的错误（media3 的 errorCodeName）。UI 必须如实显示，不能静默。 */
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * 全屏播放请求（瞬时 UI，不入路由——与全屏看图同一条约定）。
     * 非 null 时阅读页叠一层全屏播放页；由正文里的"全屏"按钮与底栏视频入口共同写入。
     */
    var fullscreenUrl by mutableStateOf<String?>(null)
        private set

    /**
     * 正文里**有没有**这条媒体的画面。由 [InlineVideoPlayer] 在组合期登记（不是 State：
     * 只在退出全屏那次回调里读，不参与重组）。
     *
     * 退出全屏时靠它区分两件事：正文里有画面 ⇒ 只是"退出全屏"，退回正文继续播；
     * 没有 ⇒ 全屏页是这条视频唯一的画面（订阅源级视频不在正文 HTML 里），
     * 那就得停掉，否则留一段"看不见的声音"。
     */
    private val inlineRenderers = mutableSetOf<String>()

    val player: Player? get() = exo

    fun isActive(url: String): Boolean = activeUrl == url

    /** 播放/暂停同一条媒体；换一条则从头开始。 */
    fun toggle(url: String, kind: MediaNodeKind) {
        val current = exo
        if (current != null && activeUrl == url && current.playbackState != Player.STATE_IDLE) {
            if (current.isPlaying) current.pause() else current.play()
            return
        }
        start(url, kind)
    }

    /**
     * 从头播一条媒体。与 [toggle] 的差别在"已在播的同一条"上：这里保持播放，
     * 不会把正在播的视频按成暂停（全屏页打开时的自动起播靠它）。
     */
    fun play(url: String, kind: MediaNodeKind) {
        val current = exo
        if (current != null && activeUrl == url && current.playbackState != Player.STATE_IDLE) {
            current.play()
            return
        }
        start(url, kind)
    }

    fun seekTo(positionMillis: Long) {
        exo?.seekTo(positionMillis.coerceAtLeast(0L))
    }

    fun pause() {
        exo?.pause()
    }

    /** 停止并回到"未开始"：播放器实例留着复用，媒体项清掉（再次播放会重新 prepare）。 */
    fun stop() {
        val current = exo
        current?.stop()
        current?.clearMediaItems()
        activeUrl = null
        playing = false
        buffering = false
        positionMs = 0L
        durationMs = 0L
        error = null
    }

    fun requestFullscreen(url: String) {
        fullscreenUrl = url
    }

    fun attachInlineRenderer(url: String) {
        inlineRenderers.add(url)
    }

    fun detachInlineRenderer(url: String) {
        inlineRenderers.remove(url)
    }

    fun hasInlineRenderer(url: String): Boolean = url in inlineRenderers

    /**
     * 退出全屏：有内嵌画面就退回正文继续播，没有才停。
     *
     * 这两种结局决定了按钮的语义与长相（[ReaderVideoPage] 据此选"退出全屏"还是"关闭"）——
     * 一个长得像"关闭"、按下去却继续播的按钮是在骗人。
     */
    fun exitFullscreen() {
        val url = fullscreenUrl
        fullscreenUrl = null
        if (url == null || !hasInlineRenderer(url)) stop()
    }

    fun release() {
        exo?.release()
        exo = null
        activeUrl = null
    }

    /** 采样播放状态与进度。轮询驱动（见 [MEDIA_TICK_MILLIS]），没有播放器时是空操作。 */
    fun sync() {
        val current = exo ?: return
        playing = current.isPlaying
        buffering = current.playbackState == Player.STATE_BUFFERING
        positionMs = current.currentPosition.coerceAtLeast(0L)
        val total = current.duration
        durationMs = if (total > 0 && total != C.TIME_UNSET) total else 0L
    }

    private fun start(url: String, kind: MediaNodeKind) {
        val target = exo ?: buildPlayer().also { exo = it }
        error = null
        activeUrl = url
        activeKind = kind
        // 换媒体先归零：否则新视频会先按上一条的宽高比撑一帧，再被 onVideoSizeChanged 纠正
        aspectRatio = MEDIA_PLACEHOLDER_ASPECT
        positionMs = 0L
        durationMs = 0L
        target.setMediaItem(MediaItem.fromUri(url))
        target.prepare()
        target.play()
    }

    private fun buildPlayer(): ExoPlayer {
        val built = ExoPlayer.Builder(context)
            // 一条播放器同时承担视频与音频，取 MOVIE：它对两类内容都是中性选择，
            // 不像 SPEECH 那样给非人声加语音处理链（PlaybackService 的播客链才用 SPEECH）
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // 拔耳机/断蓝牙自动暂停，与后台播放链一致
            .setHandleAudioBecomingNoisy(true)
            .build()
        built.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // 只按 width/height 算：旋转由播放器内部处理（media3 的
                    // unappliedRotationDegrees 已废弃且恒为 0），报出来的就是显示尺寸。
                    // pixelWidthHeightRatio 仍要参与——变形宽高比（anamorphic）靠它还原。
                    val height = videoSize.height
                    if (height > 0) {
                        aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / height
                    }
                }

                override fun onPlayerError(playbackError: PlaybackException) {
                    error = playbackError.errorCodeName
                    sync()
                }
            },
        )
        return built
    }
}

/**
 * 阅读页的正文播放器。由 [ArticleDetailScreen] 提供，正文渲染层与底栏消费。
 *
 * 默认 null（预览、未接线的宿主）：渲染层据此退回"外跳占位卡"这一安全形态，
 * 不会出现一个点了没反应的播放键。
 */
internal val LocalArticleMediaPlayer = staticCompositionLocalOf<ArticleMediaPlayer?> { null }

/**
 * 创建并托管阅读页的正文播放器。
 *
 * 释放：页面离开（组合销毁）时 release；App 退到后台（ON_STOP）先暂停——
 * 正文媒体是页面内的一次性播放，不该像播客那样退到后台继续响。
 */
@Composable
internal fun rememberArticleMediaPlayer(): ArticleMediaPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val media = remember(context) { ArticleMediaPlayer(context) }
    DisposableEffect(media) {
        onDispose { media.release() }
    }
    DisposableEffect(lifecycleOwner, media) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) media.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(media) {
        while (true) {
            media.sync()
            delay(MEDIA_TICK_MILLIS)
        }
    }
    return media
}

// ———————————————————————————————————————————————
// 渲染
// ———————————————————————————————————————————————

/**
 * 正文媒体节点的统一出口：可内嵌的直链媒体走播放器，第三方页面（iframe）保持外跳卡。
 *
 * [MediaNodeKind.EMBED] 与"没有播放器宿主"两种情况合并处理——都退回
 * [ExternalMediaCard]，即 CONTEXT.md「媒体占位卡」的原形态。
 */
@Composable
internal fun InlineMediaNode(
    node: NodeMediaCard,
    onLinkClick: (String) -> Unit,
    bottomPadding: Dp,
) {
    val media = LocalArticleMediaPlayer.current
    when {
        media == null || node.kind == MediaNodeKind.EMBED ->
            ExternalMediaCard(node, onLinkClick, bottomPadding)

        node.kind == MediaNodeKind.VIDEO -> InlineVideoPlayer(node, onLinkClick, media, bottomPadding)
        else -> InlineAudioPlayer(node, onLinkClick, media, bottomPadding)
    }
}

/** 不可内嵌的媒体（iframe 等第三方页面）：只给外跳卡，App 内不执行其脚本。 */
@Composable
private fun ExternalMediaCard(node: NodeMediaCard, onLinkClick: (String) -> Unit, bottomPadding: Dp) {
    Surface(
        color = radarColors().surface2,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, radarColors().divider),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onLinkClick(node.url) }
            .padding(bottom = bottomPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Text("▶", color = radarColors().accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = node.label,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 正文视频：未开始时是黑底占位（点一下起播），播放中换成真实画面。
 *
 * 画面用 media3 官方的 `PlayerSurface`（Compose 原生，非 AndroidView 包 PlayerView）：
 * 与正文同一套组合模型，尺寸随 [ArticleMediaPlayer.aspectRatio] 变化时由 Compose 重排。
 */
@Composable
private fun InlineVideoPlayer(
    node: NodeMediaCard,
    onLinkClick: (String) -> Unit,
    media: ArticleMediaPlayer,
    bottomPadding: Dp,
) {
    val active = media.isActive(node.url)
    val player = media.player
    val shape = RoundedCornerShape(MEDIA_CORNER_DP.dp)
    // 圆角在 clickable 之前自行补 clip（Surface 自带的那个在 modifier 之外），否则涟漪按矩形画
    val startPlay = if (active) Modifier else Modifier.clickable { media.toggle(node.url, MediaNodeKind.VIDEO) }

    // 登记"正文里有这条媒体的画面"：退出全屏时据此决定继续播还是停（见 exitFullscreen）
    DisposableEffect(node.url) {
        media.attachInlineRenderer(node.url)
        onDispose { media.detachInlineRenderer(node.url) }
    }

    Surface(
        color = Color.Black,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            // 块间距必须留在 clip 之外：padding 落在裁剪内层时下两角会变成直角
            .padding(bottom = bottomPadding)
            .clip(shape)
            .then(startPlay),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (active) media.aspectRatio else MEDIA_PLACEHOLDER_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                // 全屏页打开期间**不在这里挂画面**：一个 Player 只有一个视频输出，两个
                // PlayerSurface 同时挂，输出会留在后挂的那个（全屏页的 View）上；全屏页一关
                // 它的 View 就销毁了，而这里的 LaunchedEffect(view, player) 键值没变、
                // 不会重跑去重新挂——症状就是"声音还在、画面永远停在最后一帧"。
                // 让全屏页独占输出，关闭后本组合重新挂上即可自然恢复。
                val fullscreenOwnsOutput = media.fullscreenUrl == node.url
                if (active && player != null && !fullscreenOwnsOutput) {
                    // TextureView 而不是默认的 SurfaceView：SurfaceView 是独立图层，父级的
                    // clip（12dp 圆角）对它无效，画面会以直角溢出圆角卡；而且滚动容器里
                    // 它是"挖洞"式渲染，滚动时容易撕裂。代价是多一份 GPU 纹理，量级可接受。
                    PlayerSurface(player, Modifier.fillMaxSize(), SURFACE_TYPE_TEXTURE_VIEW)
                }
                when {
                    !active -> VideoPoster(node, media)
                    media.error != null -> Unit // 失败说明在下面控制条里，别被遮罩压住
                    !media.playing -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaPlayButton(playing = false, size = VIDEO_LOADING_SIZE_DP.dp) {
                            media.toggle(node.url, MediaNodeKind.VIDEO)
                        }
                    }
                }
                if (active && media.buffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            if (active) {
                MediaControlBar(
                    media = media,
                    url = node.url,
                    onLinkClick = onLinkClick,
                    dark = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 视频占位：黑底 + 播放键 + 来源标签（加载封面需要额外请求，正文不值当）。 */
@Composable
private fun VideoPoster(node: NodeMediaCard, media: ArticleMediaPlayer) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MediaPlayButton(playing = false, size = VIDEO_LOADING_SIZE_DP.dp) {
            media.toggle(node.url, MediaNodeKind.VIDEO)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = node.label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * 正文音频：紧凑条（图标 + 标题 + 播放键），播放时才展开时长与进度。
 *
 * 不做全屏/后台：正文里的一段音频通常几分钟，页面内听完即可；
 * 需要后台续播的长节目走订阅源级音频入口（`AudioPlayerRoute`）。
 */
@Composable
private fun InlineAudioPlayer(
    node: NodeMediaCard,
    onLinkClick: (String) -> Unit,
    media: ArticleMediaPlayer,
    bottomPadding: Dp,
) {
    val colors = radarColors()
    val active = media.isActive(node.url)
    val shape = RoundedCornerShape(MEDIA_CORNER_DP.dp)
    val startPlay = if (active) Modifier else Modifier.clickable { media.toggle(node.url, MediaNodeKind.AUDIO) }

    Surface(
        color = colors.surface2,
        shape = shape,
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .clip(shape)
            .then(startPlay),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Music,
                    contentDescription = stringResource(R.string.media_audio),
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.label,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!active) {
                    Spacer(Modifier.width(8.dp))
                    MediaPlayButton(playing = false, size = MEDIA_BUTTON_SMALL_SIZE_DP.dp) {
                        media.toggle(node.url, MediaNodeKind.AUDIO)
                    }
                }
            }
            if (active) {
                Spacer(Modifier.height(4.dp))
                MediaControlBar(media = media, url = node.url, onLinkClick = onLinkClick, dark = false)
            }
        }
    }
}

/**
 * 播放控制条：进度、播放/暂停、时间、全屏、外部打开，失败时就地给原因。
 *
 * 拖动用本地 scrubbing 值、松手才提交 seek：进度每 250ms 回写一次，
 * 不回写就会与手指抢滑杆（与播放页 AudioPlayerScreen 同一处理）。
 */
@Composable
internal fun MediaControlBar(
    media: ArticleMediaPlayer,
    url: String,
    onLinkClick: (String) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = radarColors()
    val primary = if (dark) Color.White else colors.textPrimary
    val secondary = if (dark) Color.White.copy(alpha = 0.7f) else colors.textTertiary
    var scrubbing by remember(url) { mutableStateOf<Float?>(null) }
    val duration = media.durationMs
    val hasDuration = duration > 0
    val position = media.positionMs
    val progress = scrubbing
        ?: if (hasDuration) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        SyncedSlider(
            value = progress,
            valueRange = 0f..1f,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { if (hasDuration) media.seekTo((it * duration).toLong()) }
                scrubbing = null
            },
            // 时长未知（直播流 / 还没 prepare 完）时不给拖动——拖了也无处可去
            enabled = hasDuration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaPlayButton(
                playing = media.playing,
                size = MEDIA_BUTTON_SIZE_DP.dp,
            ) {
                media.toggle(url, media.activeKind)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasDuration) {
                    "${formatMediaTime(position)} / ${formatMediaTime(duration)}"
                } else {
                    stringResource(R.string.media_unknown_duration)
                },
                color = secondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { media.requestFullscreen(url) }) {
                Icon(
                    imageVector = Lucide.Maximize2,
                    contentDescription = stringResource(R.string.media_fullscreen),
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onLinkClick(url) }) {
                Icon(
                    imageVector = Lucide.ExternalLink,
                    contentDescription = stringResource(R.string.media_open_external),
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        media.error?.let { code ->
            // UI 铁律：可能失败的交互必须让原因可见。机器码留给排查，人话留给读者，
            // 出路就是上面那颗播放键（重试）与右侧的外部打开。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.CircleAlert,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.media_playback_failed),
                    color = primary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = code,
                    color = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaPlayButton(
    playing: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Surface(
        shape = shape,
        // accent 之上的前景用 onAccent（跟强调色走），不是固定白
        color = radarColors().accent,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) Lucide.Pause else Lucide.Play,
                contentDescription = stringResource(
                    if (playing) R.string.media_pause else R.string.media_play,
                ),
                tint = radarColors().onAccent,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/** 时长文本：`m:ss`，满一小时转 `h:mm:ss`。 */
private fun formatMediaTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
