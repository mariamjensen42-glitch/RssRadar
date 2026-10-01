package com.cycling.rssradar.core.data.backup

import kotlinx.serialization.Serializable

const val BACKUP_FORMAT_VERSION = 1

enum class ImportStrategy { MERGE, OVERWRITE }

enum class ConflictPolicy { KEEP_LOCAL, KEEP_BACKUP }

@Serializable
data class BackupHeader(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
)

@Serializable
data class BackupFeed(
    val url: String,
    val title: String,
    val group: String = "",
    val iconUrl: String? = null,
    val contentType: Int = 0,
    val sourceType: Int = 0,
    val notificationsEnabled: Boolean = true,
    val fullContentEnabled: Boolean = true,
)

@Serializable
data class BackupArticle(
    val feedUrl: String,
    val link: String,
    val title: String,
    val summary: String? = null,
    val publishedAt: Long? = null,
    val fetchedAt: Long = 0L,
    val author: String? = null,
    val contentSource: Int = 0,
    val readingMinutes: Int? = null,
    val coverUrl: String? = null,
    val mediaKind: Int = 0,
    val mediaUrl: String? = null,
    val contentIncomplete: Boolean = false,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val isBookmarked: Boolean = false,
    val starredAt: Long? = null,
    val bookmarkedAt: Long? = null,
    val aiSummary: String? = null,
    val lastOpenedAt: Long? = null,
    val content: String? = null,
    val contentText: String? = null,
)

@Serializable
data class BackupAnnotation(
    val feedUrl: String,
    val articleLink: String,
    val kind: Int = 0,
    val quote: String,
    val prefix: String = "",
    val suffix: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val color: Int = 0,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupFilterRule(
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val matchType: Int = 0,
    val fieldMask: Int = 0,
    val pattern: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val scopeType: Int = 0,
    val scopeId: String? = null,
    val action: Int = 0,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupAiArtifact(
    val subjectKind: Int,
    val subjectId: Long,
    val kind: Int,
    val payload: String,
    val model: String = "",
    val inputChars: Int = 0,
    val outputChars: Int = 0,
    val createdAt: Long = 0L,
)

data class ImportReport(
    val feedsAdded: Int = 0,
    val feedsSkipped: Int = 0,
    val articlesAdded: Int = 0,
    val articlesSkipped: Int = 0,
    val annotationsAdded: Int = 0,
    val rulesAdded: Int = 0,
    val artifactsAdded: Int = 0,
) {
    val isEmpty: Boolean
        get() = feedsAdded == 0 && articlesAdded == 0 && annotationsAdded == 0 &&
            rulesAdded == 0 && artifactsAdded == 0
}

@Serializable
data class BackupLine(
    val kind: String,
    val header: BackupHeader? = null,
    val group: String? = null,
    val feed: BackupFeed? = null,
    val article: BackupArticle? = null,
    val annotation: BackupAnnotation? = null,
    val rule: BackupFilterRule? = null,
    val artifact: BackupAiArtifact? = null,
    val settings: Map<String, String>? = null,
)

object BackupKind {
    const val HEADER = "header"
    const val GROUP = "group"
    const val FEED = "feed"
    const val ARTICLE = "article"
    const val ANNOTATION = "annotation"
    const val RULE = "rule"
    const val ARTIFACT = "artifact"
    const val SETTINGS = "settings"
}
