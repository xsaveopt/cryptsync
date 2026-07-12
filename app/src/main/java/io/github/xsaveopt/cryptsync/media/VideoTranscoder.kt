package io.github.xsaveopt.cryptsync.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.data.settings.VideoCodec
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class VideoTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun transcode(
        source: File,
        output: File,
        settings: CompressionSettings,
    ): File = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val videoMime = when (settings.videoCodec) {
                VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                VideoCodec.AV1 -> MimeTypes.VIDEO_AV1
            }
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(settings.videoBitrateKbps * 1000)
                        .build(),
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(videoMime)
                .setAudioMimeType(MimeTypes.AUDIO_OPUS)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (continuation.isActive) continuation.resume(output)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                })
                .build()

            val editedMediaItem = EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.fromFile(source)),
            ).build()

            transformer.start(editedMediaItem, output.absolutePath)

            continuation.invokeOnCancellation {
                mainHandler.post { transformer.cancel() }
            }
        }
    }
}
