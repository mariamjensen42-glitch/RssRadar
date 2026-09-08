package com.cycling.rssradar.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/**
 * VM 层的消息载体（ADR-0017 §3）：VM 只给身份（res id + 结构化参数），
 * 翻译在 UI 层按当前语言进行——纯 JVM 测试不碰 android 资源。
 *
 * [Raw] 只装「不可翻译的动态数据」（外部 error message、用户输入的地址等），
 * 禁止用来装硬编码中文文案。
 */
sealed interface UiText {
    data class Res(val id: Int, val args: List<UiText> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    companion object {
        /** args 里 UiText 原样收，其余（数字/动态串）转 Raw；单一重载避免 vararg 歧义。 */
        fun res(id: Int, vararg args: Any?): Res = Res(id, args.map { toText(it) })

        private fun toText(a: Any?): UiText = when (a) {
            is UiText -> a
            else -> Raw(a?.toString().orEmpty())
        }
    }
}

/** 非 Compose 语境（Snackbar/LaunchedEffect/Toast）解析。 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(
        id,
        *args.map { it.resolve(context) }.toTypedArray(),
    )
    is UiText.Raw -> value
}

/** Composable 语境解析（嵌套参数也走 stringResource）。 */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { it.resolve() }.toTypedArray())
    is UiText.Raw -> value
}
