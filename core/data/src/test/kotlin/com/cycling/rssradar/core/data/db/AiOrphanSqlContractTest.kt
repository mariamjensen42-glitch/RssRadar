package com.cycling.rssradar.core.data.db

import com.cycling.rssradar.core.model.AiScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 孤儿清理的 SQL 契约。
 *
 * [AI_ORPHAN_PREDICATE] 把 subjectKind 写成了字面量（0 / 1）——`@Query` 只吃编译期常量，
 * 用不了枚举的构造属性。字面量与 [AiScope] 一旦脱钩，症状是**产物永远清不掉**
 * （订阅源删了、文章归档了，孤儿行还留在 ai_artifacts 里），编译与运行都不报错。
 * 这条测试把那份绑定关系变成会变红的断言。
 */
class AiOrphanSqlContractTest {

    @Test
    fun `孤儿判定的字面量与 AiScope 一致`() {
        assertEquals(0, AiScope.ARTICLE.dbValue)
        assertEquals(1, AiScope.FEED.dbValue)

        assertTrue(
            "缺文章级绑定：$AI_ORPHAN_PREDICATE",
            AI_ORPHAN_PREDICATE.contains("subjectKind = ${AiScope.ARTICLE.dbValue} AND subjectId NOT IN (SELECT id FROM articles)"),
        )
        assertTrue(
            "缺订阅源级绑定：$AI_ORPHAN_PREDICATE",
            AI_ORPHAN_PREDICATE.contains("subjectKind = ${AiScope.FEED.dbValue} AND subjectId NOT IN (SELECT id FROM feeds)"),
        )
    }

    @Test
    fun `全局级不参与孤儿清理`() {
        // 全局产物的 subjectId 固定为 0，没有父主体可查；它靠时间滚动清理（pruneGlobal）。
        // 把它混进孤儿判定会被**全部误删**——顺手补一条看似"补全"的 `subjectKind = 2`
        // 就是这么来的，所以这里反过来守住它。
        assertTrue(
            "全局级不应出现在孤儿判定里：$AI_ORPHAN_PREDICATE",
            !AI_ORPHAN_PREDICATE.contains("subjectKind = ${AiScope.GLOBAL.dbValue}"),
        )
    }
}
