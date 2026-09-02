package com.cycling.rssradar.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志兜底（issue #61）：未捕获异常写本地文件，设置页可查看与导出。
 *
 * 存在的理由：release 构建才可能出现的问题（典型是 R8 开启后的反射/序列化断裂）
 * 在用户手上是静默的——崩了就是崩了，你只能收到「不好使」三个字。这里把
 * 堆栈 + 版本 + 设备信息留在 `filesDir/crash/`，用户一键导出即可。
 *
 * 刻意的边界：
 * - 不上报、不联网（项目无 SDK 原则），只落盘；
 * - 只保留最近 [MAX_FILES] 份，崩溃日志不该无限增长；
 * - 处理器**链式转发**给原有 handler，不吞掉系统行为；
 * - 写入全程 runCatching：崩溃现场再抛异常会遮盖原始崩溃。
 */
data class CrashRecord(
    /** 日志文件名，也是读取/删除的键。 */
    val name: String,
    /** 落盘时间（epoch millis）。 */
    val time: Long,
    /** 首行摘要，通常是「异常类名: message」。 */
    val head: String,
)

object CrashLog {

    private const val DIR_NAME = "crash"
    private const val MAX_FILES = 5
    private const val MAX_TRACE_LINES = 400
    private const val PREFIX = "crash_"

    @Volatile
    private var installed = false

    /** 安装未捕获异常处理器；重复调用无副作用。 */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { record(appContext, thread, throwable) }
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    /** 崩溃记录清单，新的在前。 */
    fun list(context: Context): List<CrashRecord> = runCatching {
        dir(context).files()
            .sortedByDescending { it.name }
            .map { file -> CrashRecord(file.name, file.lastModified(), headOf(file)) }
    }.getOrDefault(emptyList())

    /** 单条日志全文；读取失败返回空串，不编造内容。 */
    fun read(context: Context, name: String): String = runCatching {
        File(dir(context), name.substringAfterLast('/')).readText()
    }.getOrDefault("")

    /** 清空全部崩溃日志。 */
    fun clear(context: Context) {
        runCatching { dir(context).files().forEach { it.delete() } }
    }

    // ---- 纯逻辑：无 Android 依赖，单测覆盖 ----

    /** 落盘并返回文件名；文件名带毫秒，同一秒两次崩溃不会互相覆盖。 */
    internal fun write(dir: File, body: String, now: Long = System.currentTimeMillis()): String {
        val name = PREFIX + fileNameTime(now) + ".log"
        File(dir, name).writeText(body)
        return name
    }

    /** 堆栈过长时截断——诚实标注截断，不静默吃掉。 */
    internal fun format(trace: String, maxLines: Int = MAX_TRACE_LINES): String {
        val lines = trace.lines()
        if (lines.size <= maxLines) return trace
        return lines.take(maxLines).joinToString("\n") +
            "\n…（堆栈过长已截断，共 ${lines.size} 行，保留前 $maxLines 行）"
    }

    /** 只保留最近 [max] 份，按文件名（= 时间戳）排序，与 lastModified 无关。 */
    internal fun prune(dir: File, max: Int = MAX_FILES) {
        dir.files()
            .sortedByDescending { it.name }
            .drop(max)
            .forEach { it.delete() }
    }

    /** 首行摘要（写入时以 `# ` 开头），读不到就退回「崩溃」。 */
    internal fun headOf(file: File): String = runCatching {
        file.useLines { lines -> lines.firstOrNull { it.isNotBlank() } ?: "" }
            .removePrefix("# ")
            .trim()
            .ifBlank { "崩溃" }
    }.getOrDefault("崩溃")

    // ---- 内部 ----

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val head = throwable.javaClass.name + (throwable.message?.let { ": $it" } ?: "")
        val body = buildString {
            appendLine("# $head")
            appendLine("RssRadar ${versionOf(context)}")
            appendLine(
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL}",
            )
            appendLine("崩溃时间：${stamp(now)}")
            appendLine("线程：${thread.name}")
            appendLine()
            append(format(throwable.stackTraceToString()))
        }
        val directory = dir(context)
        write(directory, body, now)
        prune(directory)
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 只看崩溃日志，别把 filesDir 下别的东西一起删了。 */
    private fun File.files(): List<File> =
        listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }?.toList() ?: emptyList()

    private fun versionOf(context: Context): String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrDefault("版本未知")

    private fun fileNameTime(millis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(millis))

    private fun stamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
