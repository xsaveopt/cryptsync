package io.github.xsaveopt.cryptsync.engine

import io.github.xsaveopt.cryptsync.nativebin.NativeBinaries
import io.github.xsaveopt.cryptsync.nativebin.NativeBinary
import io.github.xsaveopt.cryptsync.nativebin.ProcessRunner
import io.github.xsaveopt.cryptsync.nativebin.ProcessSpec
import io.github.xsaveopt.cryptsync.util.AppLogger
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class DriveQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
)

@Singleton
class RcloneEngine @Inject constructor(
    private val binaries: NativeBinaries,
    private val runner: ProcessRunner,
    private val rcloneConfig: RcloneConfig,
    private val logger: AppLogger,
) {
    suspend fun about(): DriveQuota? {
        if (!rcloneConfig.isConfigured()) return null
        val spec = ProcessSpec(
            executable = binaries.path(NativeBinary.RCLONE),
            args = listOf("about", "${rcloneConfig.remoteName}:", "--json"),
            env = mapOf("RCLONE_CONFIG" to rcloneConfig.path().absolutePath),
        )
        val result = runner.run(spec)
        if (!result.isSuccess) {
            logger.warn("Rclone", "Could not read Drive usage: ${result.output.takeLast(3).joinToString(" ")}")
            return null
        }
        val json = result.output.firstOrNull { it.trimStart().startsWith("{") } ?: return null
        val obj = JSONObject(json)
        val total = obj.optLong("total", -1)
        val used = obj.optLong("used", -1)
        val free = obj.optLong("free", if (total >= 0 && used >= 0) total - used else -1)
        if (total < 0 && used < 0) return null
        return DriveQuota(total, used, free)
    }
}
