package io.github.xsaveopt.cryptsync.testutil

import io.github.xsaveopt.cryptsync.nativebin.NativeBinary
import java.io.File

class FakeCli(private val context: TestContext) {
    private val responses = File(context.files, "fake-responses").apply { mkdirs() }

    init {
        val script = FakeCli::class.java.getResourceAsStream("/fake-cli.sh")!!.use { it.readBytes() }
        NativeBinary.entries.forEach { binary ->
            File(context.nativeLibs, binary.libName).apply {
                writeBytes(script)
                setExecutable(true)
            }
        }
    }

    fun respond(invocation: Int, exitCode: Int, vararg lines: String) {
        val body = buildString {
            append(exitCode).append('\n')
            lines.forEach { append(it).append('\n') }
        }
        File(responses, invocation.toString()).writeText(body)
    }

    fun calls(): List<String> =
        File(context.files, "fake-calls").takeIf { it.exists() }?.readLines() ?: emptyList()

    fun seen(invocation: Int): String? =
        File(context.files, "fake-seen-$invocation").takeIf { it.exists() }?.readText()
}
