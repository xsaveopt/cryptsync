package io.github.xsaveopt.cryptsync.media

import android.media.MediaCodecList
import io.github.xsaveopt.cryptsync.data.settings.VideoCodec

object CodecSupport {
    fun hardwareVideoCodecs(): Set<VideoCodec> {
        val hardwareEncoderTypes = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { it.isEncoder && it.isHardwareAccelerated }
            .flatMap { info -> info.supportedTypes.map { it.lowercase() } }
            .toSet()
        return VideoCodec.entries.filter { mimeFor(it) in hardwareEncoderTypes }.toSet()
    }

    private fun mimeFor(codec: VideoCodec): String = when (codec) {
        VideoCodec.HEVC -> "video/hevc"
        VideoCodec.AV1 -> "video/av01"
    }
}
