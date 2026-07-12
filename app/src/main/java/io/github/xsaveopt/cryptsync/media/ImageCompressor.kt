package io.github.xsaveopt.cryptsync.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.heifwriter.HeifWriter
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.data.settings.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressor @Inject constructor() {

    fun extensionFor(format: ImageFormat): String = when (format) {
        ImageFormat.HEIC -> "heic"
    }

    suspend fun compress(
        source: File,
        output: File,
        settings: CompressionSettings,
    ): File = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(source.absolutePath)
            ?: throw IllegalStateException("Could not decode image ${source.absolutePath}")
        try {
            when (settings.imageFormat) {
                ImageFormat.HEIC -> encodeHeic(bitmap, output, settings.imageQuality)
            }
        } finally {
            bitmap.recycle()
        }
        output
    }

    @SuppressLint("RestrictedApi")
    private fun encodeHeic(bitmap: Bitmap, output: File, quality: Int) {
        val writer = HeifWriter.Builder(
            output.absolutePath,
            bitmap.width,
            bitmap.height,
            HeifWriter.INPUT_MODE_BITMAP,
        )
            .setQuality(quality)
            .setMaxImages(1)
            .build()
        try {
            writer.start()
            writer.addBitmap(bitmap)
            writer.stop(STOP_TIMEOUT_MS)
        } finally {
            writer.close()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 30_000L
    }
}
