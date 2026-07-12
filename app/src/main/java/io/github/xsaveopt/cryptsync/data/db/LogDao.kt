package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(entry: LogEntity)

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM logs")
    suspend fun clear()
}
