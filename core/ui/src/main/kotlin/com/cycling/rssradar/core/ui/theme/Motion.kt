package com.cycling.rssradar.core.ui.theme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 全应用动效 token（docs/motion.md，issue #72）。
 *
 * 新增动画必须引用这里的常量，禁止散落 `tween(300)` 之类的魔法数。
 * 时长取的是毫秒 Int（配 tween / crossfade），曲线是 Compose Easing。
 */
object MotionTokens {
    /** 按压缩放等微交互。 */
    val DurationMicro = 120

    /** 图片 crossfade、item 删除淡出。 */
    val DurationShort = 200

    /** 页面转场。 */
    val DurationMedium = 280

    /** 通用缓动。 */
    val EasingStandard = FastOutSlowInEasing

    /** 页面转场缓动（M3 emphasized）：起步快、收尾长，层级移动的纵深感。 */
    val EasingEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * reduce-motion 信号（docs/motion.md，issue #72）：全 app 统一读 [LocalReducedMotion]。
 * 默认 false（正常动画）；由 CompositionLocalRoot 用 [rememberReducedMotion] 读系统
 * 设置后注入——观察器只在装配点注册一次，调用点直接读 Local。
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * 系统「移除动画」（无障碍 / 开发者选项把动画时长缩放置 0）时返回 true。
 *
 * 仅供装配点（CompositionLocalRoot）使用：读信号 + ContentObserver 监听
 * `ANIMATOR_DURATION_SCALE`，系统设置改动实时生效，不需要重启应用。
 * 业务代码一律读 [LocalReducedMotion]，不要直接调本函数。
 *
 * 降级原则：瞬时状态切换，不是去掉反馈。若未来要加应用内「动画开关」，只改这里。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(isAnimatorScaleZero(context)) }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isAnimatorScaleZero(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** 缩放为 0 = 用户要求无动画。默认 1f（正常动画）。 */
private fun isAnimatorScaleZero(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/**
 * 图片渐显（docs/motion.md #3）：crossfade [MotionTokens.DurationShort]；
 * reduce-motion 时关闭——所有 Coil ImageRequest 统一走这里，别再手写 crossfade。
 */
fun ImageRequest.Builder.crossfadeMotion(reducedMotion: Boolean): ImageRequest.Builder =
    if (reducedMotion) crossfade(false) else crossfade(MotionTokens.DurationShort)
