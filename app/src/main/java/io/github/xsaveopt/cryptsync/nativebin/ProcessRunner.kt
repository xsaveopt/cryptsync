package io.github.xsaveopt.cryptsync.nativebin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ProcessResult(
    val exitCode: Int,
    val output: List<String>,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

data class ProcessSpec(
    val executable: File,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val workingDir: File? = null,
    val stdin: String? = null,
)

@Singleton
class ProcessRunner @Inject constructor() {

    suspend fun run(
        spec: ProcessSpec,
        onLine: (String) -> Unit = {},
    ): ProcessResult = withContext(Dispatchers.IO) {
        val command = buildList {
            add(spec.executable.absolutePath)
            addAll(spec.args)
        }
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        spec.workingDir?.let { builder.directory(it) }
        builder.environment().putAll(spec.env)

        val process = builder.start()
        try {
            spec.stdin?.let { input ->
                process.outputStream.bufferedWriter().use { it.write(input) }
            }
            val collected = ArrayList<String>()
            process.inputStream.bufferedReader().use { reader: BufferedReader ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    collected.add(line)
                    onLine(line)
                }
            }
            val exit = process.waitFor()
            coroutineContext.ensureActive()
            ProcessResult(exit, collected)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}
