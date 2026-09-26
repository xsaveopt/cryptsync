package io.github.xsaveopt.cryptsync.data.settings

import io.github.xsaveopt.cryptsync.testutil.FakeLogDao
import io.github.xsaveopt.cryptsync.testutil.TestContext
import io.github.xsaveopt.cryptsync.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConfigBackupTest {

    private val backup = ConfigBackup(context, settings, AppLogger(FakeLogDao()))
    private val input = File(root, "input.json")

    @Before
    fun reset(): Unit = runBlocking {
        settings.setBackupLocations(emptySet())
        settings.setStorageLimit(0)
        settings.setCompression(CompressionSettings())
        settings.setSchedule(ScheduleSettings())
    }

    private fun import(json: String): Boolean = runBlocking {
        input.writeText(json)
        backup.import(input)
    }

    private fun current(): AppSettings = runBlocking { settings.settings.first() }

    @Test
    fun exportWritesConfigFileThatMatches() = runBlocking {
        val file = backup.export()
        assertEquals(File(context.files, "cryptsync-config.json"), file)
        assertTrue(backup.matches(file.absolutePath))
        assertFalse(backup.matches(file.absolutePath + ".bak"))
        assertEquals(1, JSONObject(file.readText()).getInt("version"))
    }

    @Test
    fun exportImportRoundTrips() = runBlocking {
        val compression = CompressionSettings(
            reencodeMedia = false,
            videoCodec = VideoCodec.AV1,
            videoBitrateKbps = 2500,
            audioBitrateKbps = 128,
            imageFormat = ImageFormat.HEIC,
            imageQuality = 80,
        )
        val schedule = ScheduleSettings(
            frequency = ScheduleFrequency.WEEKLY,
            hourOfDay = 22,
            chargingOnly = false,
            networkPolicy = NetworkPolicy.ANY,
        )
        settings.setBackupLocations(setOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Documents"))
        settings.setStorageLimit(15)
        settings.setCompression(compression)
        settings.setSchedule(schedule)
        val exported = backup.export().readText()

        reset()
        input.writeText(exported)
        assertTrue(backup.import(input))

        val restored = current()
        assertEquals(setOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Documents"), restored.backupLocations)
        assertEquals(15, restored.storageLimitGb)
        assertEquals(compression, restored.compression)
        assertEquals(schedule, restored.schedule)
    }

    @Test
    fun legacyKeysMergeIntoBackupLocations() {
        assertTrue(
            import(
                """{"version":1,
                   "backupLocations":["/storage/emulated/0/DCIM"],
                   "mediaDirectories":["/storage/emulated/0/Pictures","/storage/emulated/0/DCIM"],
                   "extraPaths":["/storage/emulated/0/Download"]}""",
            ),
        )
        assertEquals(
            setOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Pictures", "/storage/emulated/0/Download"),
            current().backupLocations,
        )
    }

    @Test
    fun unsafePathsAreDropped() {
        import(
            """{"backupLocations":["relative/dir","/storage/emulated/0/ok","/storage/emulated/0/bad\nname"],
               "extraPaths":["${"/" + "a".repeat(5000)}"]}""",
        )
        assertEquals(setOf("/storage/emulated/0/ok"), current().backupLocations)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        import(
            """{"storageLimitGb":-5,
               "compression":{"videoBitrateKbps":1,"audioBitrateKbps":99999,"imageQuality":500},
               "schedule":{"hourOfDay":30}}""",
        )
        val s = current()
        assertEquals(0, s.storageLimitGb)
        assertEquals(500, s.compression.videoBitrateKbps)
        assertEquals(512, s.compression.audioBitrateKbps)
        assertEquals(100, s.compression.imageQuality)
        assertEquals(23, s.schedule.hourOfDay)
    }

    @Test
    fun lowValuesAreClampedUp() {
        import(
            """{"compression":{"videoBitrateKbps":100000000,"audioBitrateKbps":1,"imageQuality":0},
               "schedule":{"hourOfDay":-3}}""",
        )
        val s = current()
        assertEquals(100_000, s.compression.videoBitrateKbps)
        assertEquals(16, s.compression.audioBitrateKbps)
        assertEquals(1, s.compression.imageQuality)
        assertEquals(0, s.schedule.hourOfDay)
    }

    @Test
    fun unknownEnumsFallBackToDefaults() {
        import(
            """{"compression":{"videoCodec":"VP9","imageFormat":"GIF"},
               "schedule":{"frequency":"YEARLY","networkPolicy":"SATELLITE"}}""",
        )
        val s = current()
        assertEquals(VideoCodec.HEVC, s.compression.videoCodec)
        assertEquals(ImageFormat.HEIC, s.compression.imageFormat)
        assertEquals(ScheduleFrequency.DAILY, s.schedule.frequency)
        assertEquals(NetworkPolicy.UNMETERED, s.schedule.networkPolicy)
    }

    @Test
    fun newerVersionIsStillImported() {
        assertTrue(import("""{"version":99,"backupLocations":["/storage/emulated/0/DCIM"],"storageLimitGb":3}"""))
        assertEquals(setOf("/storage/emulated/0/DCIM"), current().backupLocations)
        assertEquals(3, current().storageLimitGb)
    }

    @Test
    fun missingSectionsLeaveCurrentSettingsUntouched() = runBlocking {
        val compression = CompressionSettings(imageQuality = 42)
        settings.setCompression(compression)
        assertTrue(import("""{"backupLocations":[]}"""))
        assertEquals(compression, current().compression)
        assertEquals(ScheduleSettings(), current().schedule)
    }

    @Test
    fun malformedJsonReturnsFalse() {
        assertFalse(import("not json"))
    }

    companion object {
        private val root: File = Files.createTempDirectory("config-backup").toFile()
        private val context = TestContext(root)
        private val settings = SettingsRepository(context)

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            root.deleteRecursively()
        }
    }
}
