package io.github.xsaveopt.cryptsync.nativebin

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProcessRunnerTest {

    private val shell = File("/bin/sh")
    private val runner = ProcessRunner()

    private fun script(body: String, env: Map<String, String> = emptyMap(), workingDir: File? = null, stdin: String? = null) =
        ProcessSpec(executable = shell, args = listOf("-c", body), env = env, workingDir = workingDir, stdin = stdin)

    @Test
    fun specDefaults() {
        val spec = ProcessSpec(executable = shell, args = emptyList())
        assertTrue(spec.env.isEmpty())
        assertNull(spec.workingDir)
        assertNull(spec.stdin)
    }

    @Test
    fun resultSuccessFollowsExitCode() {
        assertTrue(ProcessResult(0, emptyList()).isSuccess)
        assertFalse(ProcessResult(1, emptyList()).isSuccess)
    }

    @Test
    fun collectsOutputLinesAndReportsEachOne() = runBlocking {
        val seen = mutableListOf<String>()
        val result = runner.run(script("echo one; echo two; echo three")) { seen.add(it) }
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two", "three"), result.output)
        assertEquals(result.output, seen)
    }

    @Test
    fun mergesStderrIntoOutput() = runBlocking {
        val result = runner.run(script("echo out; echo err 1>&2"))
        assertEquals(listOf("out", "err"), result.output)
    }

    @Test
    fun reportsNonZeroExitCode() = runBlocking {
        val result = runner.run(script("echo failing; exit 7"))
        assertEquals(7, result.exitCode)
        assertFalse(result.isSuccess)
        assertEquals(listOf("failing"), result.output)
    }

    @Test
    fun passesArgumentsVerbatim() = runBlocking {
        val spec = ProcessSpec(
            executable = shell,
            args = listOf("-c", "printf '%s\\n' \"\$1\" \"\$2\"", "sh", "has space", "\$HOME"),
        )
        val result = runner.run(spec)
        assertEquals(listOf("has space", "\$HOME"), result.output)
    }

    @Test
    fun addsEnvironmentVariables() = runBlocking {
        val result = runner.run(script("echo \"\$CRYPTSYNC_TEST_VALUE\"", env = mapOf("CRYPTSYNC_TEST_VALUE" to "secret value")))
        assertEquals(listOf("secret value"), result.output)
    }

    @Test
    fun runsInWorkingDirectory() = runBlocking {
        val dir = Files.createTempDirectory("process-runner").toFile()
        try {
            val result = runner.run(script("pwd -P", workingDir = dir))
            assertEquals(listOf(dir.canonicalPath), result.output)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writesStdinAndClosesIt() = runBlocking {
        val result = runner.run(script("cat", stdin = "first\nsecond\n"))
        assertEquals(listOf("first", "second"), result.output)
    }

    @Test
    fun emptyOutputGivesEmptyList() = runBlocking {
        val result = runner.run(script("true"))
        assertTrue(result.isSuccess)
        assertTrue(result.output.isEmpty())
    }

    @Test
    fun cancellationStopsReadingOutput() = runBlocking {
        val seen = mutableListOf<String>()
        val error = runCatching {
            withTimeout(500) {
                runner.run(script("while true; do echo tick; sleep 0.02; done")) { seen.add(it) }
            }
        }.exceptionOrNull()
        assertTrue(error is TimeoutCancellationException)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it == "tick" })
    }
}
