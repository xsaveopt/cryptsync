package io.github.xsaveopt.cryptsync.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.data.settings.ConfigBackup
import io.github.xsaveopt.cryptsync.engine.ResticEngine
import io.github.xsaveopt.cryptsync.engine.ResticOutput
import io.github.xsaveopt.cryptsync.engine.Snapshot
import io.github.xsaveopt.cryptsync.media.MediaCacheManager
import io.github.xsaveopt.cryptsync.util.AppLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreOutcome(
    val target: File,
    val lostFiles: List<String>,
)

data class RestoreSummary(
    val mediaPublished: Int,
    val mediaAlreadyPresent: Int,
    val mediaFailed: Int,
    val filesWritten: Int,
    val skippedNoAccess: Int,
    val configRestored: Boolean,
    val lostFiles: List<String>,
    val target: File,
)

@Singleton
class RestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resticEngine: ResticEngine,
    private val repositoryManager: RepositoryManager,
    private val mediaCacheManager: MediaCacheManager,
    private val configBackup: ConfigBackup,
    private val logger: AppLogger,
) {
    suspend fun listSnapshots(): List<Snapshot> {
        val password = repositoryManager.activePassword() ?: error("No active password")
        return resticEngine.listSnapshots(password)
    }

    suspend fun backupDirectories(snapshotId: String): List<String> {
        val password = repositoryManager.activePassword() ?: return emptyList()
        val paths = resticEngine.listNodePaths(password, snapshotId)
        val configPath = paths.firstOrNull { configBackup.matches(it) }
            ?: paths.firstOrNull { it.endsWith("/$CONFIG_NAME") }
        if (configPath != null) {
            resticEngine.dumpFile(password, snapshotId, configPath)?.let { json ->
                val dirs = RestoreDirs.backupLocationsFromConfig(json)
                if (dirs.isNotEmpty()) return dirs.sorted()
            }
        }
        return RestoreDirs.topLevelDirs(paths)
    }

    suspend fun restore(
        snapshotId: String,
        onProgress: (String) -> Unit = {},
    ): RestoreOutcome {
        val staging = stagingDir()
        val lost = runResticRestore(snapshotId, staging, onProgress)

        val target = File(defaultTarget(), snapshotId.take(8)).apply {
            deleteRecursively()
            mkdirs()
        }
        val stagingPrefix = staging.absolutePath
        val cachePrefix = mediaCacheManager.cacheDir().absolutePath + "/"
        val externalRootRel = Environment.getExternalStorageDirectory().absolutePath.trimStart('/') + "/"

        staging.walkTopDown().filter { it.isFile }.forEach { restored ->
            val original = restored.absolutePath.removePrefix(stagingPrefix)
            val relative = RestorePaths.appFolderRelative(
                original = original,
                cachePrefix = cachePrefix,
                externalRootRel = externalRootRel,
                isConfig = configBackup.matches(original),
            )
            val destination = File(target, relative)
            destination.parentFile?.mkdirs()
            restored.copyTo(destination, overwrite = true)
        }
        staging.deleteRecursively()
        logger.info(TAG, "Restore to ${target.absolutePath} finished")
        return RestoreOutcome(target, lost)
    }

    private suspend fun runResticRestore(
        snapshotId: String,
        target: File,
        onProgress: (String) -> Unit,
    ): List<String> {
        val password = repositoryManager.activePassword() ?: error("No active password")
        logger.info(TAG, "Restoring snapshot ${snapshotId.take(8)}")
        val lost = LinkedHashSet<String>()
        resticEngine.restore(password, snapshotId, target) { line ->
            onProgress(line)
            ResticOutput.restoreErrorItem(line)?.let { lost.add(it) }
        }
        if (lost.isNotEmpty()) {
            logger.error(TAG, "${lost.size} files could not be read from the backup: ${lost.joinToString(", ")}")
        }
        return lost.toList()
    }

    suspend fun restoreInPlace(
        snapshotId: String,
        onProgress: (String) -> Unit = {},
    ): RestoreSummary {
        logger.info(TAG, "Restore in place started for snapshot ${snapshotId.take(8)}")
        val staging = stagingDir()
        val lost = runResticRestore(snapshotId, staging, onProgress)

        val stagingPrefix = staging.absolutePath
        val cachePrefix = mediaCacheManager.cacheDir().absolutePath + "/"
        val externalRootRel = Environment.getExternalStorageDirectory().absolutePath.trimStart('/') + "/"
        val allFilesAccess = Environment.isExternalStorageManager()

        var mediaPublished = 0
        var mediaAlreadyPresent = 0
        var mediaFailed = 0
        var filesWritten = 0
        var skippedNoAccess = 0
        var configRestored = false

        staging.walkTopDown().filter { it.isFile }.forEach { restored ->
            val original = restored.absolutePath.removePrefix(stagingPrefix)
            when {
                configBackup.matches(original) -> {
                    configRestored = configBackup.import(restored)
                }
                original.startsWith(cachePrefix) -> {
                    val rel = RestorePaths.mediaRelative(original, cachePrefix, externalRootRel)
                    if (rel != null) {
                        when (publishToMediaStore(restored, rel)) {
                            PublishResult.PUBLISHED -> mediaPublished++
                            PublishResult.ALREADY_PRESENT -> mediaAlreadyPresent++
                            PublishResult.FAILED -> mediaFailed++
                        }
                    } else {
                        mediaFailed++
                    }
                }
                allFilesAccess -> {
                    val destination = File(original)
                    destination.parentFile?.mkdirs()
                    restored.copyTo(destination, overwrite = true)
                    filesWritten++
                }
                else -> skippedNoAccess++
            }
        }

        staging.deleteRecursively()
        logger.info(
            TAG,
            "Restore in place finished: $mediaPublished published, $mediaAlreadyPresent already present, " +
                "$mediaFailed failed, $filesWritten files in place, $skippedNoAccess skipped for access, " +
                "config ${if (configRestored) "restored" else "not found"}",
        )
        return RestoreSummary(
            mediaPublished = mediaPublished,
            mediaAlreadyPresent = mediaAlreadyPresent,
            mediaFailed = mediaFailed,
            filesWritten = filesWritten,
            skippedNoAccess = skippedNoAccess,
            configRestored = configRestored,
            lostFiles = lost,
            target = staging,
        )
    }

    private fun publishToMediaStore(source: File, relativePath: String): PublishResult {
        val name = relativePath.substringAfterLast('/')
        val directory = relativePath.substringBeforeLast('/', "")
        val extension = name.substringAfterLast('.', "").lowercase()
        val collection = when (extension) {
            in IMAGE_EXTENSIONS -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            in VIDEO_EXTENSIONS -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> return PublishResult.FAILED
        }
        val resolver = context.contentResolver
        if (mediaExists(collection, name, directory)) return PublishResult.ALREADY_PRESENT
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            mimeTypeFor(extension)?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
        }
        val uri = resolver.insert(collection, values) ?: return PublishResult.FAILED
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: run {
            resolver.delete(uri, null, null)
            return PublishResult.FAILED
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return PublishResult.PUBLISHED
    }

    private fun mediaExists(collection: Uri, name: String, directory: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, if (directory.isEmpty()) "" else "$directory/")
        return context.contentResolver.query(collection, projection, selection, args, null)
            ?.use { it.count > 0 } ?: false
    }

    private fun stagingDir(): File =
        File(context.cacheDir, "restore-staging").apply {
            deleteRecursively()
            mkdirs()
        }

    fun defaultTarget(): File =
        File(context.getExternalFilesDir(null), "restore").apply { mkdirs() }

    private fun mimeTypeFor(extension: String): String? = when (extension) {
        "heic", "heif" -> "image/heic"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        else -> null
    }

    private enum class PublishResult { PUBLISHED, ALREADY_PRESENT, FAILED }

    private companion object {
        const val TAG = "Restore"
        const val CONFIG_NAME = "cryptsync-config.json"
        val IMAGE_EXTENSIONS = setOf("heic", "heif", "jpg", "jpeg", "png", "webp", "avif")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "3gp")
    }
}
