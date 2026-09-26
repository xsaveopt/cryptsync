package io.github.xsaveopt.cryptsync.testutil

import io.github.xsaveopt.cryptsync.data.db.CacheStatus
import io.github.xsaveopt.cryptsync.data.db.LogDao
import io.github.xsaveopt.cryptsync.data.db.LogEntity
import io.github.xsaveopt.cryptsync.data.db.MediaCacheDao
import io.github.xsaveopt.cryptsync.data.db.MediaCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CopyOnWriteArrayList

class FakeLogDao : LogDao {
    val entries = CopyOnWriteArrayList<LogEntity>()

    override fun observeRecent(limit: Int): Flow<List<LogEntity>> = flowOf(entries.toList())

    override suspend fun insert(entry: LogEntity) {
        entries.add(entry)
    }

    override suspend fun prune(keep: Int) = Unit

    override suspend fun clear() {
        entries.clear()
    }
}

class FakeMediaCacheDao : MediaCacheDao {
    val rows = LinkedHashMap<String, MediaCacheEntity>()
    val upserts = ArrayList<MediaCacheEntity>()
    val deletedIds = ArrayList<String>()

    override fun observeAll(): Flow<List<MediaCacheEntity>> = flowOf(rows.values.toList())

    override suspend fun byStatus(status: CacheStatus): List<MediaCacheEntity> =
        rows.values.filter { it.status == status }

    override suspend fun find(sourceId: String): MediaCacheEntity? = rows[sourceId]

    override suspend fun readyEntries(): List<MediaCacheEntity> = byStatus(CacheStatus.READY)

    override suspend fun allSourceIds(): List<String> = rows.keys.toList()

    override suspend fun upsert(entity: MediaCacheEntity) {
        upserts.add(entity)
        rows[entity.sourceId] = entity
    }

    override suspend fun delete(entity: MediaCacheEntity) {
        rows.remove(entity.sourceId)
    }

    override suspend fun deleteByIds(ids: List<String>) {
        deletedIds.addAll(ids)
        ids.forEach { rows.remove(it) }
    }

    override fun observeCompressedBytes(): Flow<Long> =
        flowOf(rows.values.filter { it.status == CacheStatus.READY }.sumOf { it.compressedSize })
}
