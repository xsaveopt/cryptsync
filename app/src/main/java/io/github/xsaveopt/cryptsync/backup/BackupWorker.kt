package io.github.xsaveopt.cryptsync.backup

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.xsaveopt.cryptsync.data.settings.ConfigBackup
import io.github.xsaveopt.cryptsync.data.settings.SettingsRepository
import io.github.xsaveopt.cryptsync.engine.ResticEngine
import io.github.xsaveopt.cryptsync.engine.ResticOutput
import io.github.xsaveopt.cryptsync.media.MediaCacheManager
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.data.db.ActivityOutcome
import io.github.xsaveopt.cryptsync.repo.RepositoryManager
import io.github.xsaveopt.cryptsync.util.ActivityRecorder
import io.github.xsaveopt.cryptsync.util.AppLogger
import io.github.xsaveopt.cryptsync.util.formatBytes
import kotlinx.coroutines.flow.first
import java.io.File

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val mediaCacheManager: MediaCacheManager,
    private val repositoryManager: RepositoryManager,
    private val resticEngine: ResticEngine,
    private val notifications: Notifications,
    private val configBackup: ConfigBackup,
    private val logger: AppLogger,
    private val activityRecorder: ActivityRecorder,
) : CoroutineWorker(appContext, params) {

    private fun recordBackup(outcome: ActivityOutcome, detail: String) {
        activityRecorder.record(applicationContext.getString(R.string.activity_action_backup), outcome, detail)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Starting backup", "Preparing", 0, 0, true)

    override suspend fun doWork(): Result {
        val password = repositoryManager.activePassword() ?: run {
            logger.error(TAG, "Backup skipped: no password set, unlock the app first")
            return Result.failure()
        }
        val current = settings.settings.first()

        val power = applicationContext.getSystemService(PowerManager::class.java)
        if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            logger.warn(TAG, "Backup postponed: device too hot, will retry when it cools down")
            return Result.retry()
        }

        logger.info(TAG, "Backup started")
        val backupFiles = try {
            setForeground(foregroundInfo("Preparing backup", "Scanning", 0, 0, true))
            mediaCacheManager.prepare(current.backupLocations, current.compression) { progress ->
                updateProgress(
                    "Re-encoding media",
                    progress.currentName,
                    progress.processed,
                    progress.total,
                )
            }
        } catch (e: Exception) {
            logger.error(TAG, "Preparing the backup failed: ${e.message ?: e.javaClass.simpleName}")
            recordBackup(ActivityOutcome.FAILURE, applicationContext.getString(R.string.activity_backup_compression_failed))
            return Result.retry()
        }

        setForeground(foregroundInfo("Uploading backup", "Encrypting and uploading", 0, 0, true))
        if (backupFiles.isEmpty()) {
            logger.info(TAG, "Nothing to back up: no files in the selected locations")
            recordBackup(ActivityOutcome.INFO, applicationContext.getString(R.string.activity_backup_nothing))
            return Result.success()
        }
        val paths = backupFiles + configBackup.export()
        val fileCount = paths.size

        val limitGb = current.storageLimitGb
        if (limitGb > 0) {
            val limitBytes = limitGb.toLong() * BYTES_PER_GB
            setForeground(foregroundInfo("Checking storage limit", "Measuring compressed size", 0, 0, true))
            val projected = repositoryManager.projectedRepoBytes(paths)
            if (projected == null) {
                logger.warn(TAG, "Could not measure the compressed repository size, uploading without enforcing the storage limit this run")
            } else if (projected > limitBytes) {
                logger.warn(
                    TAG,
                    "Backup paused: projected repository size ${formatBytes(projected)} is over the ${formatBytes(limitBytes)} storage limit",
                )
                notifications.alert(
                    applicationContext.getString(R.string.notify_limit_title),
                    applicationContext.getString(
                        R.string.notify_limit_text,
                        formatBytes(projected),
                        formatBytes(limitBytes),
                    ),
                )
                recordBackup(
                    ActivityOutcome.FAILURE,
                    applicationContext.getString(
                        R.string.activity_backup_over_limit,
                        formatBytes(projected),
                        formatBytes(limitBytes),
                    ),
                )
                settings.setOverLimitBytes(projected)
                return Result.success()
            }
        }

        return try {
            resticEngine.backup(password, paths, tags = listOf("auto")) { line ->
                val percent = ResticOutput.backupPercent(line)
                if (percent != null) {
                    updateProgress("Uploading backup", "$percent%", percent, 100)
                } else {
                    updateProgress("Uploading backup", "Encrypting and uploading", 0, 0)
                }
            }
            repositoryManager.keepOnlyLatestSnapshot()
            settings.setOverLimitBytes(0)
            val repoSize = repositoryManager.repoSizeBytes()
            logger.info(TAG, "Backup complete: $fileCount files, repository now ${repoSize?.let { formatBytes(it) } ?: "updated"}")
            recordBackup(
                ActivityOutcome.SUCCESS,
                applicationContext.getString(R.string.activity_backup_complete, fileCount, formatBytes(repoSize ?: 0L)),
            )
            Result.success()
        } catch (e: Exception) {
            logger.error(TAG, "Upload failed: ${e.message ?: e.javaClass.simpleName}")
            recordBackup(ActivityOutcome.FAILURE, applicationContext.getString(R.string.activity_backup_failed))
            Result.retry()
        }
    }

    private fun updateProgress(title: String, text: String, processed: Int, total: Int) {
        runCatching { setForegroundAsync(foregroundInfo(title, text, processed, total, total == 0)) }
        val percent = if (total > 0) (processed * 100 / total).coerceIn(0, 100) else -1
        runCatching { setProgressAsync(workDataOf(PROGRESS to percent)) }
    }

    private fun foregroundInfo(
        title: String,
        text: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
    ): ForegroundInfo {
        val notification = notifications.build(title, text, progress, max, indeterminate)
        return ForegroundInfo(
            Notifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val WORK_NAME = "cryptsync_backup"
        const val ONE_TIME_WORK = "cryptsync_backup_oneshot"
        const val PROGRESS = "progress"
        private const val TAG = "Backup"
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
    }
}
