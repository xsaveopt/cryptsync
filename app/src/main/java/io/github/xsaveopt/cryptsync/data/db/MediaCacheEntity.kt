package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType { IMAGE, VIDEO }

enum class CacheStatus { PENDING, COMPRESSING, READY, FAILED }

@Entity(tableName = "media_cache")
data class MediaCacheEntity(
    @PrimaryKey val sourceId: String,
    val sourcePath: String,
    val sourceSize: Long,
    val sourceDateModified: Long,
    val mediaType: MediaType,
    val compressedPath: String?,
    val compressedSize: Long,
    val status: CacheStatus,
    val encodeSignature: String,
    val updatedAt: Long,
)
