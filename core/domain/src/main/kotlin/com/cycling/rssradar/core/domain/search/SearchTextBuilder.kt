package com.cycling.rssradar.core.domain.search

object SearchTextBuilder {

    const val DEFAULT_MAX_CHARS = 16384

    fun segment(raw: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(minOf(raw.length, maxChars.coerceAtLeast(0)))
        val ascii = StringBuilder()
        val cjk = StringBuilder()

        fun flushAscii() {
            if (ascii.isEmpty()) return
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(ascii)
            ascii.setLength(0)
        }

        fun flushCjk() {
            if (cjk.isEmpty()) return
            if (sb.isNotEmpty()) sb.append(' ')
            val block = cjk.toString()
            if (block.length == 1) {
                sb.append(block)
            } else {
                for (i in 0 until block.length - 1) {
                    if (i > 0) sb.append(' ')
                    sb.append(block, i, i + 2)
                }
            }
            cjk.setLength(0)
        }

        for (ch in raw) {
            when {
                isCjk(ch) -> {
                    flushAscii()
                    cjk.append(ch)
                }
                ch.isLetterOrDigit() -> {
                    flushCjk()
                    ascii.append(ch.lowercaseChar())
                }
                else -> {
                    flushAscii()
                    flushCjk()
                }
            }
        }
        flushAscii()
        flushCjk()

        val out = sb.toString()
        if (out.length <= maxChars) return out
        val cut = out.lastIndexOf(' ', maxChars)
        return if (cut <= 0) out.substring(0, maxChars) else out.substring(0, cut)
    }

    fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF ||
            code in 0x3040..0x30FF ||
            code in 0xAC00..0xD7AF
    }
}
