package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaCacheDao {

    @Query("SELECT * FROM media_cache")
    fun observeAll(): Flow<List<MediaCacheEntity>>

    @Query("SELECT * FROM media_cache WHERE status = :status")
    suspend fun byStatus(status: CacheStatus): List<MediaCacheEntity>

    @Query("SELECT * FROM media_cache WHERE sourceId = :sourceId")
    suspend fun find(sourceId: String): MediaCacheEntity?

    @Query("SELECT * FROM media_cache WHERE status = 'READY'")
    suspend fun readyEntries(): List<MediaCacheEntity>

    @Query("SELECT sourceId FROM media_cache")
    suspend fun allSourceIds(): List<String>

    @Upsert
    suspend fun upsert(entity: MediaCacheEntity)

    @Delete
    suspend fun delete(entity: MediaCacheEntity)

    @Query("DELETE FROM media_cache WHERE sourceId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COALESCE(SUM(compressedSize), 0) FROM media_cache WHERE status = 'READY'")
    fun observeCompressedBytes(): Flow<Long>
}
