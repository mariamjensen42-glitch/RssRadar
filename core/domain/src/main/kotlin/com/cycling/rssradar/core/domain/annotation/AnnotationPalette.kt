package com.cycling.rssradar.core.domain.annotation

object AnnotationPalette {

    val swatches: List<Int> = listOf(
        0xFFFFD54F.toInt(),
        0xFF81C784.toInt(),
        0xFF64B5F6.toInt(),
        0xFFF06292.toInt(),
        0xFFBA68C8.toInt(),
    )

    val defaultIndex: Int = 0

    fun colorAt(index: Int): Int = swatches[index.coerceIn(0, swatches.size - 1)]

    fun indexOfArgb(argb: Int): Int = swatches.indexOf(argb).takeIf { it >= 0 } ?: defaultIndex
}
