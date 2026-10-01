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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * 全量导入（对应 [BackupWriter] 的逐行格式）。归属键一律是「订阅源地址 + 文章链接」，
 * 不用库内自增 id——跨设备的 id 毫无可比性，拿 id 对齐只会把内容错配到别的文章上。
 *
 * 冲突策略：
 * - [ConflictPolicy.KEEP_LOCAL]：已存在的订阅源保留本机设置，只补缺的文章；
 * - [ConflictPolicy.KEEP_BACKUP]：订阅源级设置以备份为准。
 * 文章一律只补缺（已有链接不覆盖），用户状态与正文不会被备份里的旧值冲掉。
 */
class BackupReader(
    private val database: AppDatabase,
    private val settings: SettingsSnapshot,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun import(
        source: InputStream,
        strategy: ImportStrategy,
        conflict: ConflictPolicy,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportReport = withContext(ioDispatcher) {
        val json = Json { ignoreUnknownKeys = true }
        val feedIdByUrl = HashMap<String, Long>()
        var report = ImportReport()
        var processed = 0

        if (strategy == ImportStrategy.OVERWRITE) {
            database.clearAllTables()
        } else {
            database.feedDao().getAll().forEach { feedIdByUrl[it.url] = it.id }
        }

        val reader = source.bufferedReader(Charsets.UTF_8)
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parsed = runCatching { json.decodeFromString<BackupLine>(line) }.getOrNull() ?: continue
            processed++
            if (processed % PROGRESS_EVERY == 0) onProgress(processed, 0)

            when (parsed.kind) {
                BackupKind.HEADER -> {
                    val version = parsed.header?.formatVersion ?: BACKUP_FORMAT_VERSION
                    require(version <= BACKUP_FORMAT_VERSION) { "unsupported backup version $version" }
                }

                BackupKind.GROUP -> Unit

                BackupKind.FEED -> parsed.feed?.let { dto ->
                    val known = feedIdByUrl[dto.url]
                    if (known != null) {
                        if (conflict == ConflictPolicy.KEEP_BACKUP) applyFeedPreferences(known, dto)
                        report = report.copy(feedsSkipped = report.feedsSkipped + 1)
                    } else {
                        val inserted = database.feedDao().insert(dto.toEntity())
                        val resolved = if (inserted > 0) inserted else database.feedDao().findIdByUrl(dto.url)
                        if (resolved == null) {
                            report = report.copy(feedsSkipped = report.feedsSkipped + 1)
                        } else {
                            feedIdByUrl[dto.url] = resolved
                            if (inserted > 0) {
                                applyFeedPreferences(resolved, dto)
                                report = report.copy(feedsAdded = report.feedsAdded + 1)
                            } else {
                                report = report.copy(feedsSkipped = report.feedsSkipped + 1)
                            }
                        }
                    }
                }

                BackupKind.ARTICLE -> parsed.article?.let { dto ->
                    val feedId = feedIdByUrl[dto.feedUrl]
                    if (feedId == null) {
                        report = report.copy(articlesSkipped = report.articlesSkipped + 1)
                    } else {
                        val existing = database.articleDao().findIdByFeedAndLink(feedId, dto.link)
                        if (existing != null) {
                            report = report.copy(articlesSkipped = report.articlesSkipped + 1)
                        } else {
                            val ids = database.articleDao().insertAll(listOf(dto.toEntity(feedId)))
                            if ((ids.firstOrNull() ?: -1L) > 0L) {
                                report = report.copy(articlesAdded = report.articlesAdded + 1)
                            } else {
                                report = report.copy(articlesSkipped = report.articlesSkipped + 1)
                            }
                        }
                    }
                }

                BackupKind.ANNOTATION -> parsed.annotation?.let { dto ->
                    val feedId = feedIdByUrl[dto.feedUrl]
                    val articleId = feedId?.let {
                        database.articleDao().findIdByFeedAndLink(it, dto.articleLink)
                    }
                    if (articleId != null) {
                        database.annotationDao().upsert(dto.toEntity(articleId))
                        report = report.copy(annotationsAdded = report.annotationsAdded + 1)
                    }
                }

                BackupKind.RULE -> parsed.rule?.let { dto ->
                    database.filterRuleDao().upsert(dto.toEntity())
                    report = report.copy(rulesAdded = report.rulesAdded + 1)
                }

                BackupKind.ARTIFACT -> parsed.artifact?.let { dto ->
                    database.aiArtifactDao().upsert(dto.toEntity())
                    report = report.copy(artifactsAdded = report.artifactsAdded + 1)
                }

                BackupKind.SETTINGS -> parsed.settings?.let { settings.apply(it) }
            }
        }
        reader.close()
        onProgress(processed, processed)
        report
    }

    private suspend fun applyFeedPreferences(feedId: Long, dto: BackupFeed) {
        database.feedDao().updateTitle(feedId, dto.title)
        database.feedDao().updateGroup(feedId, dto.group.ifBlank { DEFAULT_GROUP })
        database.feedDao().updateContentType(feedId, dto.contentType)
        database.feedDao().updateNotificationsEnabled(feedId, dto.notificationsEnabled)
        database.feedDao().updateFullContentEnabled(feedId, dto.fullContentEnabled)
        dto.iconUrl?.let { database.feedDao().updateIconUrl(feedId, it) }
    }

    companion object {
        const val PROGRESS_EVERY = 200
    }
}

private fun BackupFeed.toEntity(): FeedEntity = FeedEntity(
    url = url,
    title = title,
    createdAt = System.currentTimeMillis(),
    groupName = group.ifBlank { DEFAULT_GROUP },
    iconUrl = iconUrl,
    sourceType = sourceType,
    contentType = contentType,
    notificationsEnabled = notificationsEnabled,
    fullContentEnabled = fullContentEnabled,
)

private fun BackupArticle.toEntity(feedId: Long): ArticleEntity = ArticleEntity(
    feedId = feedId,
    link = link,
    title = title,
    summary = summary,
    publishedAt = publishedAt,
    fetchedAt = if (fetchedAt > 0L) fetchedAt else System.currentTimeMillis(),
    content = content,
    contentText = contentText,
    author = author,
    contentSource = contentSource,
    isRead = isRead,
    isStarred = isStarred,
    isBookmarked = isBookmarked,
    starredAt = starredAt,
    bookmarkedAt = bookmarkedAt,
    readingMinutes = readingMinutes,
    coverUrl = coverUrl,
    contentIncomplete = contentIncomplete,
    aiSummary = aiSummary,
    lastOpenedAt = lastOpenedAt,
    mediaKind = mediaKind,
    mediaUrl = mediaUrl,
)

private fun BackupAnnotation.toEntity(articleId: Long): ArticleAnnotationEntity {
    val now = System.currentTimeMillis()
    return ArticleAnnotationEntity(
        articleId = articleId,
        kind = kind,
        quote = quote,
        prefix = prefix,
        suffix = suffix,
        startOffset = startOffset,
        endOffset = endOffset,
        color = color,
        note = note,
        createdAt = if (createdAt > 0L) createdAt else now,
        updatedAt = now,
    )
}

private fun BackupFilterRule.toEntity(): FilterRuleEntity {
    val now = System.currentTimeMillis()
    return FilterRuleEntity(
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
        createdAt = if (createdAt > 0L) createdAt else now,
        updatedAt = now,
    )
}

private fun BackupAiArtifact.toEntity(): AiArtifactEntity = AiArtifactEntity(
    subjectKind = subjectKind,
    subjectId = subjectId,
    kind = kind,
    payload = payload,
    model = model,
    inputChars = inputChars,
    outputChars = outputChars,
    createdAt = createdAt,
)
