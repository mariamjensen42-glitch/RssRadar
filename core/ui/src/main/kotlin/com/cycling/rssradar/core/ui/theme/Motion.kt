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
import androidx.compose.ui.platform.LocalContext

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
 * 系统「移除动画」（无障碍 / 开发者选项把动画时长缩放置 0）时返回 true。
 *
 * 单一事实来源：所有动画调用点通过本函数读信号，降级 = 瞬时状态切换，
 * 不去掉反馈。若未来要加应用内「动画开关」，只改这里。
 *
 * 用 ContentObserver 监听 `ANIMATOR_DURATION_SCALE`，系统设置改动实时生效，
 * 不需要重启应用。
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
