package com.cycling.rssradar.core.data.ai

import com.cycling.rssradar.core.data.db.dao.AI_VALUE_COLUMN
import com.cycling.rssradar.core.data.db.dao.AI_VALUE_EXPR
import com.cycling.rssradar.core.data.db.dao.AI_VALUE_FILTER_PREDICATE
import com.cycling.rssradar.core.data.db.dao.AI_VALUE_JOIN
import com.cycling.rssradar.core.data.db.dao.AI_VALUE_ORDER_BY
import com.cycling.rssradar.core.model.AiFeature
import com.cycling.rssradar.core.model.AiScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表「按信息价值排序」的 SQL 契约。
 *
 * `ArticleSql` 的 JOIN 条件把 subjectKind / kind 写成了字面量（0 / 12）——
 * `@Query` 只吃编译期常量，用不了枚举的构造属性。字面量与枚举一旦脱钩，
 * 症状是"排序开关点了没反应"（JOIN 永远匹配不到行），编译与运行都不报错。
 * 这条测试把那份绑定关系变成会变红的断言。
 */
class AiValueSqlContractTest {

    @Test
    fun `排序 SQL 的字面量与枚举一致`() {
        assertEquals(0, AiScope.ARTICLE.dbValue)
        assertEquals(12, AiFeature.NOISE.dbValue)

        assertTrue("缺 subjectKind 绑定：$AI_VALUE_JOIN", AI_VALUE_JOIN.contains("subjectKind = ${AiScope.ARTICLE.dbValue}"))
        assertTrue("缺降噪 kind 绑定：$AI_VALUE_JOIN", AI_VALUE_JOIN.contains("kind = ${AiFeature.NOISE.dbValue}"))
        assertFalse("排序 SQL 不该再引用已删的质量功能（kind = 11）：$AI_VALUE_JOIN", AI_VALUE_JOIN.contains("kind = 11"))
    }

    @Test
    fun `取值与排序都以降噪写下的信息价值分为准`() {
        assertTrue(AI_VALUE_COLUMN.startsWith("aiNoise.score"))
        assertTrue(AI_VALUE_ORDER_BY.contains("aiNoise.score DESC"))
        // 没评估过的行必须沉底而不是被排到最前：SQLite 里 NULL 最小，DESC 天然沉底，
        // 所以这里**不能**出现 IS NULL 之类的表达式（那还会让索引失效）。
        assertTrue(AI_VALUE_ORDER_BY.contains("articles.publishedAt DESC"))
    }

    @Test
    fun `阈值谓词保留未评估的文章`() {
        // 「只看值得读」只该藏"已知低价值"的，不该把"还没判"的一起藏掉——
        // 否则刚开这个开关列表会瞬间空掉，看起来像坏了。
        assertTrue(AI_VALUE_FILTER_PREDICATE.contains(":minValue IS NULL"))
        assertTrue(AI_VALUE_FILTER_PREDICATE.contains("$AI_VALUE_EXPR IS NULL"))
        assertTrue(AI_VALUE_FILTER_PREDICATE.contains("$AI_VALUE_EXPR >= :minValue"))
    }
}
