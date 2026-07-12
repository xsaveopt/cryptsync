package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.data.settings.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncodeSignatureTest {

    private val base = CompressionSettings()

    @Test
    fun videoSignatureChangesWithBitrate() {
        val a = EncodeSignature.of(MediaType.VIDEO, base.copy(videoBitrateKbps = 4000))
        val b = EncodeSignature.of(MediaType.VIDEO, base.copy(videoBitrateKbps = 2000))
        assertNotEquals(a, b)
    }

    @Test
    fun videoSignatureChangesWithCodec() {
        val a = EncodeSignature.of(MediaType.VIDEO, base.copy(videoCodec = VideoCodec.HEVC))
        val b = EncodeSignature.of(MediaType.VIDEO, base.copy(videoCodec = VideoCodec.AV1))
        assertNotEquals(a, b)
    }

    @Test
    fun imageSignatureChangesWithQuality() {
        val a = EncodeSignature.of(MediaType.IMAGE, base.copy(imageQuality = 60))
        val b = EncodeSignature.of(MediaType.IMAGE, base.copy(imageQuality = 40))
        assertNotEquals(a, b)
    }

    @Test
    fun imageSignatureIgnoresVideoSettings() {
        val a = EncodeSignature.of(MediaType.IMAGE, base.copy(videoBitrateKbps = 4000))
        val b = EncodeSignature.of(MediaType.IMAGE, base.copy(videoBitrateKbps = 1000))
        assertEquals(a, b)
    }

    @Test
    fun sameSettingsProduceSameSignature() {
        assertEquals(
            EncodeSignature.of(MediaType.VIDEO, base),
            EncodeSignature.of(MediaType.VIDEO, base.copy()),
        )
    }
}
