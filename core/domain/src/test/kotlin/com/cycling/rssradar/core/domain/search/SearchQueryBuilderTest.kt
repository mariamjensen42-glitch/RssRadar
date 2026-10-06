package com.cycling.rssradar.core.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryBuilderTest {

    @Test
    fun `多字中文走 FTS 且双字组用 AND 连接`() {
        val query = SearchQueryBuilder.build("人工智能")
        assertTrue(query is SearchQueryBuilder.Query.Fts)
        assertEquals("\"人工\" \"工智\" \"智能\"", (query as SearchQueryBuilder.Query.Fts).match)
    }

    @Test
    fun `英文走 FTS 且加引号`() {
        val query = SearchQueryBuilder.build("Kotlin")
        assertEquals("\"kotlin\"", (query as SearchQueryBuilder.Query.Fts).match)
    }

    @Test
    fun `单个汉字回退 LIKE`() {
        val query = SearchQueryBuilder.build("人")
        assertTrue(query is SearchQueryBuilder.Query.Like)
        assertEquals("人", (query as SearchQueryBuilder.Query.Like).raw)
    }

    @Test
    fun `中英混排两侧都进 FTS`() {
        val query = SearchQueryBuilder.build("Kotlin 协程")
        assertEquals("\"kotlin\" \"协程\"", (query as SearchQueryBuilder.Query.Fts).match)
    }

    @Test
    fun `空白输入返回 null`() {
        assertNull(SearchQueryBuilder.build("   "))
    }

    @Test
    fun `空串返回 null`() {
        assertNull(SearchQueryBuilder.build(""))
    }

    @Test
    fun `纯标点回退 LIKE`() {
        val query = SearchQueryBuilder.build("!!!")
        assertTrue(query is SearchQueryBuilder.Query.Like)
    }

    @Test
    fun `LIKE 回退时转义通配符`() {
        val query = SearchQueryBuilder.build("人%_")
        assertEquals("人\\%\\_", (query as SearchQueryBuilder.Query.Like).raw)
    }

    @Test
    fun `查询与索引用同一套分词非字母数字当作分隔符`() {
        val query = SearchQueryBuilder.build("a\"b")
        assertEquals("\"a\" \"b\"", (query as SearchQueryBuilder.Query.Fts).match)
    }

    @Test
    fun `混合单字与多字时整体回退 LIKE`() {
        val query = SearchQueryBuilder.build("人工 人")
        assertTrue(query is SearchQueryBuilder.Query.Like)
    }
}
