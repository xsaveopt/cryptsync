package io.github.xsaveopt.cryptsync.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResticOutputTest {

    @Test
    fun parsesSnapshotsFromMixedOutput() {
        val output = listOf(
            "some progress noise",
            """[{"short_id":"abc12345","time":"2026-07-04T16:19:56Z","paths":["/x/y"],"hostname":"android"}]""",
        )
        val snapshots = ResticOutput.parseSnapshots(output)
        assertEquals(1, snapshots.size)
        assertEquals("abc12345", snapshots.first().id)
        assertEquals(listOf("/x/y"), snapshots.first().paths)
    }

    @Test
    fun parseSnapshotsReturnsEmptyWhenNoJson() {
        assertTrue(ResticOutput.parseSnapshots(listOf("no json here")).isEmpty())
    }

    @Test
    fun parsesKeysAndMarksCurrent() {
        val output = listOf(
            """[{"id":"key1","userName":"u","hostName":"h","created":"2026-07-04","current":true},""" +
                """{"id":"key2","userName":"u","hostName":"h","created":"2026-07-03","current":false}]""",
        )
        val keys = ResticOutput.parseKeys(output)
        assertEquals(2, keys.size)
        assertEquals("key1", keys.first().id)
        assertTrue(keys.first().current)
    }

    @Test
    fun restoreErrorItemExtractsFailingItem() {
        val line = """{"message_type":"error","error":{"message":"x"},"item":"/dcim/a.heic"}"""
        assertEquals("/dcim/a.heic", ResticOutput.restoreErrorItem(line))
    }

    @Test
    fun restoreErrorItemIgnoresNonErrorMessages() {
        assertNull(ResticOutput.restoreErrorItem("""{"message_type":"status","percent_done":0.5}"""))
        assertNull(ResticOutput.restoreErrorItem("plain log line"))
    }

    @Test
    fun backupPercentScalesFraction() {
        assertEquals(50, ResticOutput.backupPercent("""{"message_type":"status","percent_done":0.5}"""))
        assertEquals(0, ResticOutput.backupPercent("""{"message_type":"status","percent_done":0.0}"""))
        assertEquals(100, ResticOutput.backupPercent("""{"message_type":"status","percent_done":1.0}"""))
    }

    @Test
    fun backupPercentIgnoresNonStatusLines() {
        assertNull(ResticOutput.backupPercent("""{"message_type":"summary"}"""))
        assertNull(ResticOutput.backupPercent("not json"))
    }

    @Test
    fun backupAddedBytesPrefersPackedSize() {
        val output = listOf(
            """{"message_type":"status","percent_done":0.9}""",
            """{"message_type":"summary","data_added":5000,"data_added_packed":1200}""",
        )
        assertEquals(1200L, ResticOutput.backupAddedBytes(output))
    }

    @Test
    fun backupAddedBytesFallsBackToUnpacked() {
        val output = listOf("""{"message_type":"summary","data_added":5000}""")
        assertEquals(5000L, ResticOutput.backupAddedBytes(output))
    }

    @Test
    fun backupAddedBytesNullWhenNoSummary() {
        assertNull(ResticOutput.backupAddedBytes(listOf("""{"message_type":"status"}""", "noise")))
    }

    @Test
    fun rawDataSizeReadsTotalSize() {
        val output = listOf("""{"total_size":34567,"total_blob_count":42,"snapshots_count":1}""")
        assertEquals(34567L, ResticOutput.rawDataSize(output))
    }

    @Test
    fun rawDataSizeNullWhenAbsent() {
        assertNull(ResticOutput.rawDataSize(listOf("not json", """{"other":1}""")))
    }

    @Test
    fun lsPathsCollectsNodesAndSkipsSnapshotLine() {
        val output = listOf(
            """{"time":"2026-07-04T20:25:00Z","paths":["/x"],"struct_type":"snapshot"}""",
            """{"name":"DCIM","type":"dir","path":"/storage/emulated/0/DCIM","struct_type":"node"}""",
            """{"name":"a.jpg","type":"file","path":"/storage/emulated/0/DCIM/a.jpg","struct_type":"node"}""",
            "progress noise",
        )
        assertEquals(
            listOf("/storage/emulated/0/DCIM", "/storage/emulated/0/DCIM/a.jpg"),
            ResticOutput.lsPaths(output),
        )
    }
}
