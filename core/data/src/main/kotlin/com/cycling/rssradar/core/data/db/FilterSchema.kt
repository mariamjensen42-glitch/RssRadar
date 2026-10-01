package com.cycling.rssradar.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "filter_rules",
    indices = [Index("enabled"), Index("priority")],
)
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
    @ColumnInfo(defaultValue = "0") val matchType: Int = 0,
    @ColumnInfo(defaultValue = "0") val fieldMask: Int = 0,
    val pattern: String,
    @ColumnInfo(defaultValue = "0") val caseSensitive: Boolean = false,
    @ColumnInfo(defaultValue = "0") val wholeWord: Boolean = false,
    @ColumnInfo(defaultValue = "0") val scopeType: Int = 0,
    val scopeId: String? = null,
    @ColumnInfo(defaultValue = "0") val action: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface FilterRuleDao {

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    suspend fun getAll(): List<FilterRuleEntity>

    @Query("SELECT * FROM filter_rules WHERE id = :id")
    suspend fun getById(id: Long): FilterRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FilterRuleEntity): Long

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE filter_rules SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long)

    @Query("UPDATE filter_rules SET priority = :priority, updatedAt = :now WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int, now: Long)

    @Query("SELECT COUNT(*) FROM filter_rules")
    suspend fun count(): Int
}
