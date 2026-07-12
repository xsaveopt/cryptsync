package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val type: MediaType?,
)

@Singleton
class SourceScanner @Inject constructor() {
    suspend fun scan(locations: Set<String>): List<ScannedFile> = withContext(Dispatchers.IO) {
        locations.flatMap { location ->
            val root = File(location)
            if (!root.exists()) {
                emptyList()
            } else {
                root.walkTopDown()
                    .onEnter { dir -> !dir.isExcludedDir() }
                    .filter { it.isFile && it.name != NOMEDIA }
                    .map {
                        ScannedFile(
                            path = it.absolutePath,
                            size = it.length(),
                            lastModified = it.lastModified(),
                            type = FileClassifier.classify(it.name),
                        )
                    }
                    .toList()
            }
        }
    }

    private fun File.isExcludedDir(): Boolean =
        name == THUMBNAILS || File(this, NOMEDIA).exists()

    private companion object {
        const val NOMEDIA = ".nomedia"
        const val THUMBNAILS = ".thumbnails"
    }
}
