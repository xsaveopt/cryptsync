package io.github.xsaveopt.cryptsync.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSanitizerTest {

    @Test
    fun acceptsAbsolutePathWithSpaces() {
        assertTrue(ConfigSanitizer.isSafePath("/storage/emulated/0/My Folder/pic.jpg"))
    }

    @Test
    fun rejectsRelativePath() {
        assertFalse(ConfigSanitizer.isSafePath("relative/path"))
    }

    @Test
    fun rejectsControlCharacters() {
        assertFalse(ConfigSanitizer.isSafePath("/storage/emulated/0/tab\tname"))
        assertFalse(ConfigSanitizer.isSafePath("/storage/emulated/0/newline\nname"))
    }

    @Test
    fun rejectsOverlyLongPath() {
        assertFalse(ConfigSanitizer.isSafePath("/" + "a".repeat(4096)))
    }

    @Test
    fun enumOrDefaultParsesKnownValue() {
        assertEquals(VideoCodec.AV1, ConfigSanitizer.enumOrDefault("AV1", VideoCodec.HEVC))
    }

    @Test
    fun enumOrDefaultFallsBackForUnknownValue() {
        assertEquals(VideoCodec.HEVC, ConfigSanitizer.enumOrDefault("NONSENSE", VideoCodec.HEVC))
    }

    @Test
    fun enumOrDefaultFallsBackForNullOrEmpty() {
        assertEquals(VideoCodec.HEVC, ConfigSanitizer.enumOrDefault(null, VideoCodec.HEVC))
        assertEquals(VideoCodec.HEVC, ConfigSanitizer.enumOrDefault("", VideoCodec.HEVC))
    }
}
