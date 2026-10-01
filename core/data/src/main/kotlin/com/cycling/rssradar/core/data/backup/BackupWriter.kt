package com.cycling.rssradar.core.data.backup

import com.cycling.rssradar.core.data.db.AiArtifactEntity
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.db.ArticleAnnotationEntity
import com.cycling.rssradar.core.data.db.entity.ArticleEntity
import com.cycling.rssradar.core.data.db.DEFAULT_GROUP
import com.cycling.rssradar.core.data.db.entity.FeedEntity
import com.cycling.rssradar.core.data.db.FilterRuleEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * 全量导出：**一行一条记录**（JSON Lines），不是单个大 JSON 文档。
 *
 * 理由是可验证的：数万篇文章带正文时，单个文档必须先在内存里拼出完整字符串再写盘，
 * 序列化峰值等于备份体积本身（几百 MB 直接 OOM）；逐行写则常驻内存恒定，
 * 导出中断也留下可读的半份文件。读侧对应 [BackupReader] 的逐行解析。
 *
 * 不导出 API Key：设置快照来自 SettingsPrefs.of，密钥在另一个文件里。
 */
class BackupWriter(
    private val database: AppDatabase,
    private val settings: SettingsSnapshot,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun export(
        sink: OutputStream,
        appVersion: String,
        includeContent: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int = withContext(ioDispatcher) {
        val json = Json { encodeDefaults = false }
        val out = BufferedWriter(OutputStreamWriter(sink, Charsets.UTF_8))
        var emitted = 0

        fun emit(line: BackupLine) {
            out.write(json.encodeToString(line))
            out.write("\n")
            emitted++
        }

        emit(
            BackupLine(
                kind = BackupKind.HEADER,
                header = BackupHeader(
                    exportedAt = System.currentTimeMillis(),
                    appVersion = appVersion,
                ),
            ),
        )

        val feeds = database.feedDao().getAll()
        feeds.map { it.groupName.ifBlank { DEFAULT_GROUP } }
            .distinct()
            .forEach { emit(BackupLine(kind = BackupKind.GROUP, group = it)) }
        feeds.forEach { emit(BackupLine(kind = BackupKind.FEED, feed = it.toBackup())) }

        val feedUrlById = feeds.associate { it.id to it.url }
        val articleRefs = HashMap<Long, Pair<String, String>>()
        val total = database.articleDao().countAll()
        var offset = 0
        while (true) {
            val rows = database.articleDao().pageAllArticles(EXPORT_BATCH, offset)
            if (rows.isEmpty()) break
            rows.forEach { entity ->
                val feedUrl = feedUrlById[entity.feedId] ?: return@forEach
                emit(BackupLine(kind = BackupKind.ARTICLE, article = entity.toBackup(feedUrl, includeContent)))
                articleRefs[entity.id] = feedUrl to entity.link
            }
            offset += rows.size
            onProgress(offset, total)
        }

        database.annotationDao().getAll().forEach { entity ->
            val ref = articleRefs[entity.articleId] ?: return@forEach
            emit(
                BackupLine(
                    kind = BackupKind.ANNOTATION,
                    annotation = entity.toBackup(ref.first, ref.second),
                ),
            )
        }

        database.filterRuleDao().getAll().forEach { entity ->
            emit(BackupLine(kind = BackupKind.RULE, rule = entity.toBackup()))
        }

        database.aiArtifactDao().globalArtifacts().forEach { entity ->
            emit(BackupLine(kind = BackupKind.ARTIFACT, artifact = entity.toBackup()))
        }

        emit(BackupLine(kind = BackupKind.SETTINGS, settings = settings.read()))

        out.flush()
        emitted
    }

    companion object {
        const val EXPORT_BATCH = 200
    }
}

private fun FeedEntity.toBackup(): BackupFeed = BackupFeed(
    url = url,
    title = title,
    group = groupName.ifBlank { DEFAULT_GROUP },
    iconUrl = iconUrl,
    contentType = contentType,
    sourceType = sourceType,
    notificationsEnabled = notificationsEnabled,
    fullContentEnabled = fullContentEnabled,
)

private fun ArticleEntity.toBackup(feedUrl: String, includeContent: Boolean): BackupArticle = BackupArticle(
    feedUrl = feedUrl,
    link = link,
    title = title,
    summary = summary,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    author = author,
    contentSource = contentSource,
    readingMinutes = readingMinutes,
    coverUrl = coverUrl,
    mediaKind = mediaKind,
    mediaUrl = mediaUrl,
    contentIncomplete = contentIncomplete,
    isRead = isRead,
    isStarred = isStarred,
    isBookmarked = isBookmarked,
    starredAt = starredAt,
    bookmarkedAt = bookmarkedAt,
    aiSummary = aiSummary,
    lastOpenedAt = lastOpenedAt,
    content = content.takeIf { includeContent },
    contentText = contentText.takeIf { includeContent },
)

private fun ArticleAnnotationEntity.toBackup(feedUrl: String, link: String): BackupAnnotation = BackupAnnotation(
    feedUrl = feedUrl,
    articleLink = link,
    kind = kind,
    quote = quote,
    prefix = prefix,
    suffix = suffix,
    startOffset = startOffset,
    endOffset = endOffset,
    color = color,
    note = note,
    createdAt = createdAt,
)

private fun FilterRuleEntity.toBackup(): BackupFilterRule = BackupFilterRule(
    name = name,
    enabled = enabled,
    priority = priority,
    matchType = matchType,
    fieldMask = fieldMask,
    pattern = pattern,
    caseSensitive = caseSensitive,
    wholeWord = wholeWord,
    scopeType = scopeType,
    scopeId = scopeId,
    action = action,
    createdAt = createdAt,
)

private fun AiArtifactEntity.toBackup(): BackupAiArtifact = BackupAiArtifact(
    subjectKind = subjectKind,
    subjectId = subjectId,
    kind = kind,
    payload = payload,
    model = model,
    inputChars = inputChars,
    outputChars = outputChars,
    createdAt = createdAt,
)
