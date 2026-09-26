package io.github.xsaveopt.cryptsync.engine

import io.github.xsaveopt.cryptsync.nativebin.NativeBinaries
import io.github.xsaveopt.cryptsync.nativebin.ProcessRunner
import io.github.xsaveopt.cryptsync.testutil.FakeCli
import io.github.xsaveopt.cryptsync.testutil.FakeLogDao
import io.github.xsaveopt.cryptsync.testutil.TestContext
import io.github.xsaveopt.cryptsync.util.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RcloneEngineTest {

    private lateinit var root: File
    private lateinit var context: TestContext
    private lateinit var cli: FakeCli
    private lateinit var config: RcloneConfig
    private lateinit var engine: RcloneEngine

    @Before
    fun setUp() {
        root = Files.createTempDirectory("rclone-engine").toFile()
        context = TestContext(root)
        cli = FakeCli(context)
        config = RcloneConfig(context)
        engine = RcloneEngine(NativeBinaries(context), ProcessRunner(), config, AppLogger(FakeLogDao()))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun configure() {
        config.path().writeText("[gdrive]\ntype = drive\n")
    }

    @Test
    fun returnsNullWithoutRunningWhenNotConfigured() = runBlocking {
        assertNull(engine.about())
        assertTrue(cli.calls().isEmpty())
    }

    @Test
    fun parsesTotalUsedAndFree() = runBlocking {
        configure()
        cli.respond(1, 0, """{"total":1000,"used":400,"free":550,"trashed":10}""")
        assertEquals(DriveQuota(1000, 400, 550), engine.about())
        assertEquals(listOf("about gdrive: --json"), cli.calls())
    }

    @Test
    fun computesFreeWhenMissing() = runBlocking {
        configure()
        cli.respond(1, 0, """{"total":1000,"used":400}""")
        assertEquals(DriveQuota(1000, 400, 600), engine.about())
    }

    @Test
    fun freeUnknownWhenTotalMissing() = runBlocking {
        configure()
        cli.respond(1, 0, """{"used":400}""")
        assertEquals(DriveQuota(-1, 400, -1), engine.about())
    }

    @Test
    fun skipsNoiseBeforeJson() = runBlocking {
        configure()
        cli.respond(1, 0, "NOTICE: something harmless", """  {"total":10,"used":4}""")
        assertEquals(DriveQuota(10, 4, 6), engine.about())
    }

    @Test
    fun returnsNullWhenNeitherTotalNorUsedPresent() = runBlocking {
        configure()
        cli.respond(1, 0, """{"free":5}""")
        assertNull(engine.about())
    }

    @Test
    fun returnsNullWhenNoJsonLine() = runBlocking {
        configure()
        cli.respond(1, 0, "no json here")
        assertNull(engine.about())
    }

    @Test
    fun returnsNullOnFailure() = runBlocking {
        configure()
        cli.respond(1, 1, "Failed to about: couldn't fetch token")
        assertNull(engine.about())
    }
}
