package com.cycling.rssradar.core.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 音频/播客播放服务（media3）。
 *
 * 为什么必须有服务而不是在播放页里直接持有 ExoPlayer：播客动辄半小时起，
 * 用户会锁屏、切到别的 App、甚至退出播放页——播放器活在前台服务里，这些都不中断，
 * 通知栏与锁屏的播放控制也由 [MediaSessionService] 一并提供。
 *
 * 音频属性声明成**语音**（[C.AUDIO_CONTENT_TYPE_SPEECH]）：播客是人声，
 * 系统据此选择通话式音频处理链，也影响车机与蓝牙的行为。
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // 拔耳机/断蓝牙自动暂停，避免外放惊吓
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 用户从最近任务里划掉 App：没在播就顺手收掉服务，不留空壳前台通知。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
