package com.cycling.rssradar.core.ui.theme

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
 * 动效方案：直接用 M3 Expressive 官方的 [MotionScheme]（2026-10-03 改）。
 *
 * 原先这里有一套自定的 `MotionTokens`（DurationMicro 120 / Short 200 / Medium 280 +
 * FastOutSlowIn 与 Emphasized 两条曲线），现已删除——官方 scheme 提供成套的、经过设计的规格：
 * - `defaultSpatialSpec()`：空间位移（页面转场、跟手拖拽）
 * - `defaultEffectsSpec()`：效果变化（缩放、颜色、淡入淡出）
 * - 各另有 fast / slow 变体，覆盖微交互到长距离移动
 *
 * **为什么要按这两类分**：M3 Expressive 把动效分成 spatial（元素在空间里移动）与
 * effects（元素在原地改变外观）两族，用错会让转场显得生硬。本项目对应关系：
 * - 页面转场（MainActivity 的 enter / exit）→ spatial
 * - 列表项动画、crossfade、按压缩放 → effects
 *
 * 这些 spec 由 MaterialTheme 的 motionScheme 提供，读取入口是 [spatialSpec] / [effectsSpec]。
 */

/**
 * 官方动效方案里的「效果变化」规格（缩放 / 淡入淡出 / 颜色）。
 *
 * 官方把这四个规格做成了 `MaterialTheme.motionScheme` 的扩展属性，字节码里对应
 * `MotionSchemeKt` 里带 `getMotionScheme` 的 Composable 函数——所以入口是
 * `MaterialTheme.motionScheme.xxxSpec()`，不能当顶层函数 import。
 */
@Composable
fun effectsSpec(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()

/** 官方动效方案里的「效果变化」快档（退场淡出用：比进场快才跟手）。 */
@Composable
fun fastEffectsSpec(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()

/** 官方动效方案里的「空间位移」规格（页面转场 / 跟手拖拽）。 */
@Composable
fun <T> spatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

/** 官方动效方案里的「空间位移」快档（退场用）。 */
@Composable
fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

/**
 * reduce-motion 信号（docs/motion.md，issue #72）：全 app 统一读 [LocalReducedMotion]。
 *
 * 这不是「自定义动效」而是**无障碍**：系统「移除动画」开启时必须让动效降级为瞬时。
 * M3 官方 scheme 只管动效长什么样，不管用户是否要求关掉，所以这一层必须自己留。
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
 * 图片渐显（docs/motion.md #3）：Coil 的 crossfade 只认毫秒 Int、不认 AnimationSpec，
 * 所以这里取官方 effects 规格同量级的固定值（200ms，与旧的自定 Short 一致）。
 * reduce-motion 时关闭——所有 Coil ImageRequest 统一走这里，别再手写 crossfade。
 */
fun ImageRequest.Builder.crossfadeMotion(reducedMotion: Boolean): ImageRequest.Builder =
    if (reducedMotion) crossfade(false) else crossfade(EFFECTS_CROSSFADE_MILLIS)

/** 官方 effects 规格同量级的渐显时长（ms）。 */
private const val EFFECTS_CROSSFADE_MILLIS = 200
