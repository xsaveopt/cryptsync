package io.github.xsaveopt.cryptsync.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.data.db.CacheStatus
import io.github.xsaveopt.cryptsync.data.db.MediaCacheDao
import io.github.xsaveopt.cryptsync.data.db.MediaCacheEntity
import io.github.xsaveopt.cryptsync.data.db.MediaType
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.util.AppLogger
import io.github.xsaveopt.cryptsync.util.formatBytes
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CacheProgress(val processed: Int, val total: Int, val currentName: String)

@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: SourceScanner,
    private val videoTranscoder: VideoTranscoder,
    private val imageCompressor: ImageCompressor,
    private val dao: MediaCacheDao,
    private val logger: AppLogger,
) {
    val compressedBytes: Flow<Long> = dao.observeCompressedBytes()

    fun cacheDir(): File =
        File(context.getExternalFilesDir(null), "media_cache").apply { mkdirs() }

    suspend fun prepare(
        locations: Set<String>,
        settings: CompressionSettings,
        onProgress: (CacheProgress) -> Unit = {},
    ): List<File> {
        val scanned = scanner.scan(locations)
        val media = if (settings.reencodeMedia) scanned.filter { it.type != null } else emptyList()
        val nonMedia = if (settings.reencodeMedia) scanned.filter { it.type == null } else scanned
        if (!settings.reencodeMedia) {
            logger.info(TAG_SCAN, "Media re-encoding is off, backing up all ${scanned.size} files as they are")
        } else {
            logger.info(
                TAG_SCAN,
                "Scanned ${locations.size} locations: ${media.count { it.type == MediaType.IMAGE }} images, " +
                    "${media.count { it.type == MediaType.VIDEO }} videos, ${nonMedia.size} other files",
            )
        }

        propagateDeletions(scanned.map { it.path }.toSet())

        val pending = media.filter { needsCompression(it, settings) }
        if (pending.isEmpty()) {
            logger.info(TAG_COMPRESS, "No media to re-encode, cache already up to date")
        } else {
            logger.info(TAG_COMPRESS, "Re-encoding ${pending.size} new or changed media items")
        }
        pending.forEachIndexed { index, item ->
            onProgress(CacheProgress(index, pending.size, File(item.path).name))
            compress(item, settings)
        }

        val backupFiles = ArrayList<File>()
        media.forEach { item ->
            val compressed = dao.find(item.path)
                ?.takeIf { it.status == CacheStatus.READY }
                ?.compressedPath
                ?.let(::File)
                ?.takeIf { it.exists() }
            backupFiles.add(compressed ?: File(item.path))
        }
        nonMedia.forEach { backupFiles.add(File(it.path)) }
        return backupFiles.filter { it.exists() }
    }

    private suspend fun propagateDeletions(scannedPaths: Set<String>) {
        val existing = dao.allSourceIds().toSet()
        val removed = existing - scannedPaths
        if (removed.isEmpty()) return
        removed.forEach { id ->
            dao.find(id)?.compressedPath?.let { File(it).delete() }
        }
        dao.deleteByIds(removed.toList())
        logger.info(TAG_SCAN, "Removed ${removed.size} deleted items from the cache")
    }

    private suspend fun needsCompression(file: ScannedFile, settings: CompressionSettings): Boolean {
        val type = file.type ?: return false
        val existing = dao.find(file.path) ?: return true
        return existing.status != CacheStatus.READY ||
            existing.sourceDateModified != file.lastModified ||
            existing.sourceSize != file.size ||
            existing.encodeSignature != EncodeSignature.of(type, settings)
    }

    private suspend fun compress(file: ScannedFile, settings: CompressionSettings) {
        val type = file.type ?: return
        val name = File(file.path).name
        val tag = if (type == MediaType.VIDEO) TAG_VIDEO else TAG_IMAGE
        val source = File(file.path)
        if (!source.exists()) {
            logger.warn(tag, "Skipped $name, source no longer exists")
            return
        }

        val extension = if (type == MediaType.VIDEO) {
            "mp4"
        } else {
            imageCompressor.extensionFor(settings.imageFormat)
        }
        val output = mirroredPath(file.path, extension)
        output.parentFile?.mkdirs()

        val signature = EncodeSignature.of(type, settings)
        dao.upsert(entityFor(file, type, output, CacheStatus.COMPRESSING, 0, signature))
        try {
            when (type) {
                MediaType.VIDEO -> videoTranscoder.transcode(source, output, settings)
                MediaType.IMAGE -> imageCompressor.compress(source, output, settings)
            }
            dao.upsert(entityFor(file, type, output, CacheStatus.READY, output.length(), signature))
            logger.info(tag, "$name ${formatBytes(file.size)} to ${formatBytes(output.length())}")
        } catch (e: Exception) {
            output.delete()
            dao.upsert(entityFor(file, type, output, CacheStatus.FAILED, 0, signature))
            logger.error(tag, "Failed on $name, backing up the original instead: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun mirroredPath(sourcePath: String, extension: String): File {
        val relative = sourcePath.trimStart('/')
        val base = File(cacheDir(), relative)
        val withoutExt = base.absolutePath.substringBeforeLast('.', base.absolutePath)
        return File("$withoutExt.$extension")
    }

    private fun entityFor(
        file: ScannedFile,
        type: MediaType,
        output: File,
        status: CacheStatus,
        size: Long,
        signature: String,
    ) = MediaCacheEntity(
        sourceId = file.path,
        sourcePath = file.path,
        sourceSize = file.size,
        sourceDateModified = file.lastModified,
        mediaType = type,
        compressedPath = output.absolutePath,
        compressedSize = size,
        status = status,
        encodeSignature = signature,
        updatedAt = System.currentTimeMillis(),
    )

    private companion object {
        const val TAG_SCAN = "Scan"
        const val TAG_COMPRESS = "Compress"
        const val TAG_IMAGE = "Image"
        const val TAG_VIDEO = "Video"
    }
}
