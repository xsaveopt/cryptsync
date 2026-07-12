package io.github.xsaveopt.cryptsync.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun bytesUnderKilobyteShownAsBytes() {
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun kilobytesFormatted() {
        assertEquals("1.0 KB", formatBytes(1024))
    }

    @Test
    fun megabytesFormatted() {
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gigabytesFormatted() {
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun snapshotTimeFormattedFromRfc3339() {
        assertEquals("Jul 4, 2026 at 20:25", formatSnapshotTime("2026-07-04T20:25:30.123456789+00:00"))
    }

    @Test
    fun snapshotTimeFallsBackToRawWhenUnparseable() {
        assertEquals("not a date", formatSnapshotTime("not a date"))
    }
}
