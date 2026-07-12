package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings

object EncodeSignature {
    fun of(type: MediaType, settings: CompressionSettings): String = when (type) {
        MediaType.VIDEO ->
            "v:${settings.videoCodec.name}:${settings.videoBitrateKbps}:${settings.audioBitrateKbps}"
        MediaType.IMAGE ->
            "i:${settings.imageFormat.name}:${settings.imageQuality}"
    }
}
