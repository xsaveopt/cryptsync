package io.github.xsaveopt.cryptsync.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.nativebin.NativeBinaries
import io.github.xsaveopt.cryptsync.nativebin.NativeBinary
import io.github.xsaveopt.cryptsync.nativebin.ProcessResult
import io.github.xsaveopt.cryptsync.nativebin.ProcessRunner
import io.github.xsaveopt.cryptsync.nativebin.ProcessSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Snapshot(
    val id: String,
    val time: String,
    val paths: List<String>,
    val hostname: String,
)

data class RepoKey(
    val id: String,
    val userName: String,
    val hostName: String,
    val created: String,
    val current: Boolean,
)

class ResticException(val result: ProcessResult) :
    Exception("restic failed (${result.exitCode}): ${result.output.takeLast(5).joinToString("\n")}")

@Singleton
class ResticEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val binaries: NativeBinaries,
    private val runner: ProcessRunner,
    private val rcloneConfig: RcloneConfig,
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "restic").apply { mkdirs() }
    }
    private val tmpDir: File by lazy {
        File(context.cacheDir, "tmp").apply { mkdirs() }
    }

    private fun repository(): String =
        "rclone:${rcloneConfig.remoteName}:cryptsync"

    private fun baseEnv(password: String): Map<String, String> = mapOf(
        "RESTIC_PASSWORD" to password,
        "RESTIC_REPOSITORY" to repository(),
        "RESTIC_CACHE_DIR" to cacheDir.absolutePath,
        "RCLONE_CONFIG" to rcloneConfig.path().absolutePath,
        "TMPDIR" to tmpDir.absolutePath,
        "HOME" to context.filesDir.absolutePath,
    )

    private fun rcloneProgramArgs(): List<String> = listOf(
        "-o", "rclone.program=${binaries.path(NativeBinary.RCLONE).absolutePath}",
    )

    private suspend fun restic(
        args: List<String>,
        password: String,
        onLine: (String) -> Unit = {},
    ): ProcessResult {
        val first = runProcess(args, password, onLine)
        if (first.isSuccess) return first
        if (isLockFailure(first) && "unlock" !in args) {
            runProcess(listOf("unlock"), password) {}
            val retry = runProcess(args, password, onLine)
            if (!retry.isSuccess) throw ResticException(retry)
            return retry
        }
        throw ResticException(first)
    }

    private suspend fun runProcess(
        args: List<String>,
        password: String,
        onLine: (String) -> Unit,
    ): ProcessResult {
        val spec = ProcessSpec(
            executable = binaries.path(NativeBinary.RESTIC),
            args = rcloneProgramArgs() + args,
            env = baseEnv(password),
            workingDir = tmpDir,
        )
        return runner.run(spec, onLine)
    }

    private fun isLockFailure(result: ProcessResult): Boolean =
        result.output.any {
            it.contains("unable to create lock", ignoreCase = true) ||
                it.contains("already locked", ignoreCase = true)
        }

    suspend fun initRepository(password: String): ProcessResult =
        restic(listOf("init"), password)

    suspend fun isInitialized(password: String): Boolean =
        try {
            restic(listOf("cat", "config"), password)
            true
        } catch (e: ResticException) {
            false
        }

    suspend fun backup(
        password: String,
        paths: List<File>,
        tags: List<String> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): ProcessResult {
        val listFile = File.createTempFile("backup-paths", ".txt", tmpDir)
        listFile.writeText(paths.joinToString("\n") { it.absolutePath })
        return try {
            val args = buildList {
                add("backup")
                add("--json")
                tags.forEach {
                    add("--tag")
                    add(it)
                }
                add("--files-from")
                add(listFile.absolutePath)
            }
            restic(args, password, onProgress)
        } finally {
            listFile.delete()
        }
    }

    suspend fun dryRunAddedBytes(password: String, paths: List<File>): Long? {
        val listFile = File.createTempFile("dryrun-paths", ".txt", tmpDir)
        listFile.writeText(paths.joinToString("\n") { it.absolutePath })
        return try {
            val result = restic(
                buildList {
                    add("backup")
                    add("--dry-run")
                    add("--json")
                    add("--files-from")
                    add(listFile.absolutePath)
                },
                password,
            )
            ResticOutput.backupAddedBytes(result.output)
        } catch (e: ResticException) {
            null
        } finally {
            listFile.delete()
        }
    }

    suspend fun rawDataSize(password: String): Long? =
        try {
            ResticOutput.rawDataSize(restic(listOf("stats", "--mode", "raw-data", "--json"), password).output)
        } catch (e: ResticException) {
            null
        }

    suspend fun forgetKeepLast(password: String, keepLast: Int, prune: Boolean): ProcessResult {
        val args = buildList {
            add("forget")
            add("--keep-last")
            add(keepLast.toString())
            if (prune) add("--prune")
        }
        return restic(args, password)
    }

    suspend fun listSnapshots(password: String): List<Snapshot> =
        ResticOutput.parseSnapshots(restic(listOf("snapshots", "--json"), password).output)

    suspend fun listNodePaths(password: String, snapshotId: String): List<String> =
        try {
            ResticOutput.lsPaths(restic(listOf("ls", snapshotId, "--json"), password).output)
        } catch (e: ResticException) {
            emptyList()
        }

    suspend fun dumpFile(password: String, snapshotId: String, path: String): String? =
        try {
            restic(listOf("dump", snapshotId, path), password).output.joinToString("\n")
        } catch (e: ResticException) {
            null
        }

    suspend fun restore(
        password: String,
        snapshotId: String,
        target: File,
        onProgress: (String) -> Unit = {},
    ): ProcessResult {
        target.mkdirs()
        return try {
            restic(
                listOf("restore", snapshotId, "--target", target.absolutePath, "--json"),
                password,
                onProgress,
            )
        } catch (e: ResticException) {
            e.result
        }
    }

    suspend fun check(password: String, onLine: (String) -> Unit = {}): ProcessResult =
        try {
            restic(listOf("check"), password, onLine)
        } catch (e: ResticException) {
            e.result
        }

    suspend fun filesInPack(password: String, packId: String): ProcessResult =
        try {
            restic(listOf("find", "--pack", packId), password)
        } catch (e: ResticException) {
            e.result
        }

    suspend fun repairIndex(password: String): ProcessResult =
        try {
            restic(listOf("repair", "index"), password)
        } catch (e: ResticException) {
            e.result
        }

    suspend fun repairSnapshots(password: String): ProcessResult =
        try {
            restic(listOf("repair", "snapshots", "--forget"), password)
        } catch (e: ResticException) {
            e.result
        }

    suspend fun forget(password: String, snapshotId: String, prune: Boolean): ProcessResult {
        val args = buildList {
            add("forget")
            add(snapshotId)
            if (prune) add("--prune")
        }
        return restic(args, password)
    }

    suspend fun addKey(currentPassword: String, newPassword: String): ProcessResult {
        val newPwFile = File.createTempFile("newpw", null, tmpDir)
        return try {
            newPwFile.writeText(newPassword)
            restic(
                listOf("key", "add", "--new-password-file", newPwFile.absolutePath),
                currentPassword,
            )
        } finally {
            newPwFile.delete()
        }
    }

    suspend fun listKeys(password: String): List<RepoKey> =
        ResticOutput.parseKeys(restic(listOf("key", "list", "--json"), password).output)

    suspend fun removeKey(password: String, keyId: String): ProcessResult =
        restic(listOf("key", "remove", keyId), password)

    suspend fun unlock(password: String): ProcessResult =
        restic(listOf("unlock"), password)
}
