package com.cycling.rssradar.di

import androidx.room.Room
import androidx.room.withTransaction
import com.cycling.rssradar.core.data.db.AppDatabase
import com.cycling.rssradar.core.data.refresh.TransactionRunner

/** 生产事务 adapter：委托 Room 的 withTransaction。 */
internal class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        db.withTransaction { block() }
}
