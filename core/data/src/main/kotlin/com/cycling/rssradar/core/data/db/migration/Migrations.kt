package com.cycling.rssradar.core.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：增加文章已读/收藏/稍后读/阅读时长/封面字段，给 feeds 加 groupName / iconUrl。
 * 旧库直接清空（开发期允许，生产前需要写真正的迁移脚本）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN readingMinutes INTEGER")
        db.execSQL("ALTER TABLE articles ADD COLUMN coverUrl TEXT")
        db.execSQL("ALTER TABLE feeds ADD COLUMN groupName TEXT NOT NULL DEFAULT '默认'")
        db.execSQL("ALTER TABLE feeds ADD COLUMN iconUrl TEXT")
    }
}

/**
 * v2 → v3：文章增加正文列（content/contentText/author/contentSource）。
 * 依据 ADR-0001：正文与摘要分离，summary 回归"短摘要"语义。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN content TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN contentText TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN contentSource INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3 → v4：feeds 增加订阅源类型（sourceType）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN sourceType INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5：articles 增加 AI 摘要列（aiSummary）。见 ADR-0005。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN aiSummary TEXT")
    }
}

/**
 * v5 → v6：feeds 增加自动同步开关（syncEnabled，issue #58）。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v6 → v7：feeds 增加 Feed 级预设——全文抓取开关（fullContentEnabled，issue #9）。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN fullContentEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v7 → v8：正文抓取可观测性。
 * - articles.contentIncomplete：正文被判定为「不完整」的标记（ADR-0012）。
 * - content_fetch_log：每次按需抓取留一条记录（链接/站点/状态码/重试次数/页数/原因），
 *   诊断页与"哪些站点抓不到正文"的归因都依赖它。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN contentIncomplete INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS content_fetch_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                link TEXT NOT NULL,
                host TEXT NOT NULL,
                statusCode INTEGER,
                attempts INTEGER NOT NULL,
                pages INTEGER NOT NULL,
                ok INTEGER NOT NULL,
                failure TEXT,
                issue TEXT,
                contentChars INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_content_fetch_log_host ON content_fetch_log (host)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_content_fetch_log_createdAt ON content_fetch_log (createdAt)")
    }
}

/**
 * v8 → v9：归档/清空墓碑表（issue「归档后刷新文章复活」）。
 * 真删的文章留一笔 (feedId, link)，刷新 upsert 跳过墓碑，杜绝「删了又同步回来」。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_article_tombstones (
                feedId INTEGER NOT NULL,
                link TEXT NOT NULL,
                archivedAt INTEGER NOT NULL,
                PRIMARY KEY(feedId, link)
            )
            """.trimIndent(),
        )
    }
}

/** v9 → v10（#31）：feeds 增加 Feed 级通知开关列，默认开（存量源行为不变）。 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE feeds ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1",
        )
    }
}

/**
 * v10 → v11：推荐流（ADR-0013）。
 * - articles.lastOpenedAt：最近一次打开详情页的时间，画像的唯一采集信号。
 * - recommendation_feedback：一个订阅源一行的降权系数（「减少此类」的负反馈，可撤销）。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN lastOpenedAt INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recommendation_feedback (
                feedId INTEGER NOT NULL,
                penalty REAL NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(feedId)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v11 → v12（#65）：给 articles 的列表排序补索引。
 *
 * 索引名必须和 Room 从 @Entity 生成的一模一样（index_<表>_<列1>_<列2>），
 * 否则新装用户（建表时自动建索引）和升级用户（走这条 migration）的 schema 会分叉。
 *
 * 只建索引、不改表结构，所以对存量数据零影响，也不需要重写任何行。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_articles_publishedAt_fetchedAt` " +
                "ON `articles` (`publishedAt`, `fetchedAt`)",
        )
    }
}

/**
 * v12 → v13（ADR-0014 内容分类）：feeds.contentType + articles.mediaKind。
 * 都是带默认值的 int 新列，存量行用默认值（文章类/跟随 feed），无需重写。
 *
 * contentType 对存量 feed 做一次 SQL 回填：类型预判原本只在订阅时跑，老源
 * 会永远停留在「文章」，图片源永远变不成画廊。回填只认高置信域名/路由信号
 * （与 FeedContentTypeGuesser 同一套关键词），猜不出保持文章类；用户之后仍可在
 * 订阅操作页改。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN contentType INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE articles ADD COLUMN mediaKind INTEGER NOT NULL DEFAULT 0")
        // 图片
        db.execSQL(
            "UPDATE feeds SET contentType = 1 WHERE contentType = 0 AND (" +
                "url LIKE '%pixiv.net%' OR url LIKE '%rsshub%/pixiv%' OR " +
                "url LIKE '%instagram.com%')",
        )
        // 视频
        db.execSQL(
            "UPDATE feeds SET contentType = 2 WHERE contentType = 0 AND (" +
                "url LIKE '%bilibili.com%' OR url LIKE '%rsshub%/bilibili%' OR " +
                "url LIKE '%youtube.com%' OR url LIKE '%/youtube/%' OR " +
                "url LIKE '%douyin.com%')",
        )
        // 音频
        db.execSQL(
            "UPDATE feeds SET contentType = 3 WHERE contentType = 0 AND (" +
                "url LIKE '%xiaoyuzhoufm.com%' OR " +
                "url LIKE '%podcast%')",
        )
    }
}

/**
 * v14 → v15：增量刷新的 HTTP 协商凭证（feeds.etag / feeds.lastModified）。
 * 都是不带默认值的可空 TEXT 新列，存量行取 null（下次刷新走普通请求拿到凭证），
 * 无需重写任何行。
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN etag TEXT")
        db.execSQL("ALTER TABLE feeds ADD COLUMN lastModified TEXT")
    }
}

/**
 * v15 → v16（#82 失效源检测）：feeds 加 3 列——连续失败计数 / 失败分类 / 恢复时间。
 * consecutiveFailures 带默认值 0（与实体 @ColumnInfo(defaultValue="0") 一致，
 * Room 的 schema 校验认这个）；两个可空列无默认值，存量行取 null（= 健康/从未失败）。
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feeds ADD COLUMN consecutiveFailures INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE feeds ADD COLUMN failureReason TEXT")
        db.execSQL("ALTER TABLE feeds ADD COLUMN lastSuccessAt INTEGER")
    }
}

/**
 * v16 → v17：为四组新能力一次补齐 schema——收藏/稍后读时间戳与媒体地址、预分词检索语料、
 * 本地过滤规则表、正文标注表、全文检索索引表。
 *
 * articles 的四个新列**一律不写 DEFAULT**：实体上没有 `@ColumnInfo(defaultValue=)`，
 * Room 生成的建表语句也就没有 DEFAULT，迁移里多写一个 DEFAULT 会让 onValidateSchema
 * 判定不符、老用户升级后一开 App 就崩。存量收藏/稍后读的时间戳用 fetchedAt 近似回填。
 *
 * articles_fts 用**独立 FTS4 表**而不是外部内容表（FTS4 的 content= 形式），因为后者要求
 * 迁移里手写 Room 生成的那组 room_fts_content_sync_* 触发器，名字与语句必须与其 KSP 产物
 * 逐字符一致，而本机跑不了 gradle、拿不到产物去核对——写错就是"搜索永远搜不到新文章"
 * 且没有任何自动化能拦。独立表的同步改为写入路径显式维护（RefreshEngine + SearchIndexWorker 兜底）。
 *
 * 存疑点已实测排除：`FTS4(searchText, tokenize=unicode61)` 建出来的表，PRAGMA table_info
 * 只返回 searchText（rowid 是隐式主键，不出现在列集合里），与 Room 对 @Fts4 实体的列期望一致。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN starredAt INTEGER")
        db.execSQL("ALTER TABLE articles ADD COLUMN bookmarkedAt INTEGER")
        db.execSQL("ALTER TABLE articles ADD COLUMN mediaUrl TEXT")
        db.execSQL("ALTER TABLE articles ADD COLUMN searchText TEXT")

        db.execSQL("UPDATE articles SET starredAt = fetchedAt WHERE isStarred = 1 AND starredAt IS NULL")
        db.execSQL("UPDATE articles SET bookmarkedAt = fetchedAt WHERE isBookmarked = 1 AND bookmarkedAt IS NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `filter_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `matchType` INTEGER NOT NULL DEFAULT 0,
                `fieldMask` INTEGER NOT NULL DEFAULT 0,
                `pattern` TEXT NOT NULL,
                `caseSensitive` INTEGER NOT NULL DEFAULT 0,
                `wholeWord` INTEGER NOT NULL DEFAULT 0,
                `scopeType` INTEGER NOT NULL DEFAULT 0,
                `scopeId` TEXT,
                `action` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_enabled` ON `filter_rules` (`enabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_priority` ON `filter_rules` (`priority`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `article_annotations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `kind` INTEGER NOT NULL DEFAULT 0,
                `quote` TEXT NOT NULL,
                `prefix` TEXT NOT NULL,
                `suffix` TEXT NOT NULL,
                `startOffset` INTEGER NOT NULL DEFAULT 0,
                `endOffset` INTEGER NOT NULL DEFAULT 0,
                `color` INTEGER NOT NULL DEFAULT 0,
                `note` TEXT,
                `contentHash` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_annotations_articleId` ON `article_annotations` (`articleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_annotations_createdAt` ON `article_annotations` (`createdAt`)")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` " +
                "USING FTS4(`searchText`, tokenize=unicode61)",
        )
    }
}

/**
 * v13 → v14（AI 智能功能模块）：ai_artifacts / feed_ai_profiles / ai_tasks 三张新表。
 *
 * 全部是新增表，不改动任何既有列，存量数据零影响。
 *
 * 两条写在血泪里的约束：
 * 1. 索引名必须与 Room 从 `@Entity` 生成的名字**逐字符一致**（`index_<表>_<列1>_<列2>`），
 *    否则新装用户与升级用户的 schema 会分叉——Room 校验时才发现，且报错信息极难定位。
 * 2. **只有带 `@ColumnInfo(defaultValue = ...)` 的字段才有 SQL DEFAULT**。
 *    光写 Kotlin 默认值（`val status: Int = 0`）Room 不会生成 SQL DEFAULT——
 *    它只在构造 entity 时用这个值。migration 里多写一个 DEFAULT 就会让
 *    `onValidateSchema` 判定不符，升级用户一开 App 就崩。
 *    本文件里因此只有 `priority` / `inputChars` / `outputChars` 三列带 DEFAULT。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_artifacts` (
                `subjectKind` INTEGER NOT NULL,
                `subjectId` INTEGER NOT NULL,
                `kind` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `model` TEXT NOT NULL,
                `inputChars` INTEGER NOT NULL DEFAULT 0,
                `outputChars` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`subjectKind`, `subjectId`, `kind`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_artifacts_kind` ON `ai_artifacts` (`kind`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_artifacts_createdAt` ON `ai_artifacts` (`createdAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `feed_ai_profiles` (
                `feedId` INTEGER NOT NULL,
                `summaryPrompt` TEXT,
                `autoSummary` INTEGER,
                `autoTags` INTEGER,
                `autoClassify` INTEGER,
                `autoScore` INTEGER,
                `watchHealth` INTEGER,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`feedId`)
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ai_tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `kind` INTEGER NOT NULL,
                `targetId` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `runAfter` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastError` TEXT,
                `dedupeKey` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ai_tasks_status_runAfter` ON `ai_tasks` (`status`, `runAfter`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tasks_dedupeKey` ON `ai_tasks` (`dedupeKey`)")
    }
}
