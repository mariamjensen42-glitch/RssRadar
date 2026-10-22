package com.cycling.rssradar.core.ui.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/**
 * VM 层的消息载体：VM 只给身份（res id + 结构化参数），
 * 翻译在 UI 层按当前语言进行——纯 JVM 测试不碰 android 资源。
 *
 * [Raw] 只装「不可翻译的动态数据」（外部 error message、用户输入的地址等），
 * 禁止用来装硬编码中文文案。
 *
 * 为什么在 core:ui 而不在 app 的 i18n 包：各 feature 的 ViewModel 都要用它，
 * 而 feature 摸不到 app 的包（依赖方向 feature → core）。它本身只依赖
 * res id 与 Context，不含任何具体文案，正好是「跨 feature 的 UI 层契约」。
 */
sealed interface UiText {
    data class Res(val id: Int, val args: List<Any?> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    companion object {
        /**
         * args 原样收（UiText 递归解析，其余保持原类型交给 String.format）。
         *
         * **数字必须保持数字**：把它提前 `toString()` 再进 `%1$d` 会抛
         * `IllegalFormatConversionException`（`%d` 只吃 Byte/Short/Int/Long/BigInteger）。
         * 资源里写 `%d` 还是 `%s` 由文案决定，载体不替它做转换。
         */
        fun res(id: Int, vararg args: Any?): Res = Res(id, args.toList())
    }
}

private fun Any?.resolveArg(context: Context): Any = when (this) {
    is UiText -> resolve(context)
    null -> ""
    else -> this
}

@Composable
@ReadOnlyComposable
private fun Any?.resolveArgComposable(): Any = when (this) {
    is UiText -> resolve()
    null -> ""
    else -> this
}

/** 非 Compose 语境（Snackbar/LaunchedEffect/Toast）解析。 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(
        id,
        *args.map { it.resolveArg(context) }.toTypedArray(),
    )
    is UiText.Raw -> value
}

/** Composable 语境解析（嵌套参数也走 stringResource）。 */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { it.resolveArgComposable() }.toTypedArray())
    is UiText.Raw -> value
}
