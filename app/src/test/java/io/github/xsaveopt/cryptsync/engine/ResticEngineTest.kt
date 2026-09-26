package io.github.xsaveopt.cryptsync.engine

import io.github.xsaveopt.cryptsync.nativebin.NativeBinaries
import io.github.xsaveopt.cryptsync.nativebin.ProcessRunner
import io.github.xsaveopt.cryptsync.testutil.FakeCli
import io.github.xsaveopt.cryptsync.testutil.TestContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResticEngineTest {

    private lateinit var root: File
    private lateinit var context: TestContext
    private lateinit var cli: FakeCli
    private lateinit var engine: ResticEngine

    @Before
    fun setUp() {
        root = Files.createTempDirectory("restic-engine").toFile()
        context = TestContext(root)
        cli = FakeCli(context)
        engine = ResticEngine(context, NativeBinaries(context), ProcessRunner(), RcloneConfig(context))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun tmpFiles(): List<File> =
        File(context.cache, "tmp").listFiles()?.toList() ?: emptyList()

    private fun expectResticFailure(block: suspend () -> Unit): ResticException {
        val error = runCatching { runBlocking { block() } }.exceptionOrNull()
        assertTrue("expected ResticException but got $error", error is ResticException)
        return error as ResticException
    }

    @Test
    fun lockFailureUnlocksThenRetriesOnce() = runBlocking {
        cli.respond(1, 1, "unable to create lock in backend: repository is already locked")
        cli.respond(3, 0, "retried")
        val result = engine.forgetKeepLast("pw", keepLast = 1, prune = true)
        assertTrue(result.isSuccess)
        assertEquals(listOf("retried"), result.output)
        assertEquals(
            listOf("forget --keep-last 1 --prune", "unlock", "forget --keep-last 1 --prune"),
            cli.calls(),
        )
    }

    @Test
    fun alreadyLockedMessageAlsoTriggersUnlock() = runBlocking {
        cli.respond(1, 1, "Fatal: repository is Already Locked exclusively")
        engine.removeKey("pw", "abc")
        assertEquals(listOf("key remove abc", "unlock", "key remove abc"), cli.calls())
    }

    @Test
    fun secondLockFailureThrowsWithoutAnotherUnlock() {
        cli.respond(1, 1, "unable to create lock")
        cli.respond(3, 3, "unable to create lock again")
        val error = expectResticFailure { engine.forget("pw", "abc", prune = false) }
        assertEquals(3, error.result.exitCode)
        assertEquals(listOf("forget abc", "unlock", "forget abc"), cli.calls())
    }

    @Test
    fun unlockIsNeverRetriedOnLockFailure() {
        cli.respond(1, 1, "unable to create lock")
        expectResticFailure { engine.unlock("pw") }
        assertEquals(listOf("unlock"), cli.calls())
    }

    @Test
    fun nonLockFailureThrowsWithoutRetry() {
        cli.respond(1, 1, "wrong password or no key found")
        val error = expectResticFailure { engine.listSnapshots("pw") }
        assertEquals(1, error.result.exitCode)
        assertTrue(error.message!!.contains("wrong password"))
        assertEquals(listOf("snapshots --json"), cli.calls())
    }

    @Test
    fun exceptionMessageKeepsOnlyLastFiveLines() {
        cli.respond(1, 2, "l1", "l2", "l3", "l4", "l5", "l6", "l7")
        val error = expectResticFailure { engine.initRepository("pw") }
        assertEquals("restic failed (2): l3\nl4\nl5\nl6\nl7", error.message)
    }

    @Test
    fun isInitializedSwallowsFailure() = runBlocking {
        cli.respond(1, 10, "Fatal: repository does not exist")
        assertFalse(engine.isInitialized("pw"))
        assertTrue(engine.isInitialized("pw"))
        assertEquals(listOf("cat config", "cat config"), cli.calls())
    }

    @Test
    fun readersSwallowFailureIntoEmptyValues() = runBlocking {
        (1..4).forEach { cli.respond(it, 1, "boom") }
        assertNull(engine.rawDataSize("pw"))
        assertEquals(emptyList<String>(), engine.listNodePaths("pw", "snap"))
        assertNull(engine.dumpFile("pw", "snap", "/data/file.txt"))
        assertNull(engine.dryRunAddedBytes("pw", listOf(File("/data/a.txt"))))
    }

    @Test
    fun resultReturningWrappersSwallowFailure() = runBlocking {
        (1..5).forEach { cli.respond(it, 1, "failed $it") }
        val target = File(root, "target")
        assertEquals(listOf("failed 1"), engine.restore("pw", "snap", target).output)
        assertEquals(listOf("failed 2"), engine.check("pw").output)
        assertEquals(listOf("failed 3"), engine.filesInPack("pw", "pack").output)
        assertEquals(listOf("failed 4"), engine.repairIndex("pw").output)
        assertEquals(listOf("failed 5"), engine.repairSnapshots("pw").output)
        assertTrue(target.isDirectory)
        assertEquals(
            listOf(
                "restore snap --target ${target.absolutePath} --json",
                "check",
                "find --pack pack",
                "repair index",
                "repair snapshots --forget",
            ),
            cli.calls(),
        )
    }

    @Test
    fun mutatingWrappersPropagateFailure() {
        (1..8).forEach { cli.respond(it, 1, "failed") }
        expectResticFailure { engine.initRepository("pw") }
        expectResticFailure { engine.backup("pw", listOf(File("/data/a.txt"))) }
        expectResticFailure { engine.forgetKeepLast("pw", 1, prune = false) }
        expectResticFailure { engine.listSnapshots("pw") }
        expectResticFailure { engine.forget("pw", "snap", prune = true) }
        expectResticFailure { engine.addKey("pw", "new") }
        expectResticFailure { engine.listKeys("pw") }
        expectResticFailure { engine.removeKey("pw", "key") }
    }

    @Test
    fun backupPassesPathsThroughTempFileAndDeletesIt() = runBlocking {
        val paths = listOf(File("/data/one.txt"), File("/data/two words.txt"))
        engine.backup("pw", paths, tags = listOf("auto", "manual"))
        val call = cli.calls().single()
        assertTrue(call.startsWith("backup --json --tag auto --tag manual --files-from "))
        assertEquals("/data/one.txt\n/data/two words.txt", cli.seen(1))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun backupDeletesTempFileWhenResticFails() {
        cli.respond(1, 1, "failed")
        expectResticFailure { engine.backup("pw", listOf(File("/data/one.txt"))) }
        assertEquals("/data/one.txt", cli.seen(1))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun dryRunParsesAddedBytesAndDeletesTempFile() = runBlocking {
        cli.respond(
            1,
            0,
            """{"message_type":"summary","data_added":1234,"data_added_packed":1000}""",
        )
        val added = engine.dryRunAddedBytes("pw", listOf(File("/data/one.txt")))
        assertEquals(1000L, added)
        assertTrue(cli.calls().single().startsWith("backup --dry-run --json --files-from "))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun dryRunDeletesTempFileWhenResticFails() = runBlocking {
        cli.respond(1, 1, "failed")
        assertNull(engine.dryRunAddedBytes("pw", listOf(File("/data/one.txt"))))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun addKeyWritesNewPasswordToTempFileAndDeletesIt() = runBlocking {
        engine.addKey("old", "new secret")
        assertTrue(cli.calls().single().startsWith("key add --new-password-file "))
        assertEquals("new secret", cli.seen(1))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun addKeyDeletesTempFileWhenResticFails() {
        cli.respond(1, 1, "failed")
        expectResticFailure { engine.addKey("old", "new secret") }
        assertEquals("new secret", cli.seen(1))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun retryAfterLockStillCleansTempFile() = runBlocking {
        cli.respond(1, 1, "unable to create lock")
        engine.backup("pw", listOf(File("/data/one.txt")))
        assertEquals(3, cli.calls().size)
        assertEquals("/data/one.txt", cli.seen(3))
        assertTrue(tmpFiles().isEmpty())
    }
}
