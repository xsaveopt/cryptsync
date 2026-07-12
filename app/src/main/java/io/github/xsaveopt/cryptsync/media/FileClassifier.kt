package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType

object FileClassifier {
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
    private val VIDEO = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v", "mts", "m2ts", "wmv", "flv")

    fun classify(name: String): MediaType? = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE -> MediaType.IMAGE
        in VIDEO -> MediaType.VIDEO
        else -> null
    }
}
