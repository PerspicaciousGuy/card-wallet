package com.cardwallet.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    suspend fun getAll(): List<CardRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CardRecord)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Room over SQLite with ciphertext-only rows (Decision 2). Database version
 * covers the TABLE shape; the per-row schemaVersion covers the encrypted
 * payload shape — payload migrations happen after decrypt, not in SQL.
 */
@Database(entities = [CardRecord::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
