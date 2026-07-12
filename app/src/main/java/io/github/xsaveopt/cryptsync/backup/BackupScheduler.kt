package io.github.xsaveopt.cryptsync.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import io.github.xsaveopt.cryptsync.data.settings.NetworkPolicy
import io.github.xsaveopt.cryptsync.data.settings.ScheduleFrequency
import io.github.xsaveopt.cryptsync.data.settings.ScheduleSettings
import java.time.Duration
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BackupProgress(val running: Boolean, val percent: Int)

@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun progress(): Flow<BackupProgress> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.ONE_TIME_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.WORK_NAME),
    ) { oneTime, periodic ->
        val manual = oneTime.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        val scheduled = periodic.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val active = manual ?: scheduled
        BackupProgress(
            running = active != null,
            percent = active?.progress?.getInt(BackupWorker.PROGRESS, -1) ?: -1,
        )
    }

    fun apply(schedule: ScheduleSettings) {
        if (schedule.frequency == ScheduleFrequency.MANUAL) {
            workManager.cancelUniqueWork(BackupWorker.WORK_NAME)
            return
        }

        val interval = intervalFor(schedule.frequency)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(interval)
            .setConstraints(constraintsFor(schedule))
            .setInitialDelay(initialDelayMinutes(schedule), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .build()

        workManager.enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().build())
            .build()
        workManager.enqueueUniqueWork(
            BackupWorker.ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelRunning() {
        workManager.cancelUniqueWork(BackupWorker.ONE_TIME_WORK)
    }

    private fun intervalFor(frequency: ScheduleFrequency): Duration = when (frequency) {
        ScheduleFrequency.HOURLY -> Duration.ofHours(1)
        ScheduleFrequency.DAILY -> Duration.ofDays(1)
        ScheduleFrequency.WEEKLY -> Duration.ofDays(7)
        ScheduleFrequency.MONTHLY -> Duration.ofDays(30)
        ScheduleFrequency.MANUAL -> Duration.ofDays(365)
    }

    private fun constraintsFor(schedule: ScheduleSettings): Constraints {
        val networkType = when (schedule.networkPolicy) {
            NetworkPolicy.UNMETERED -> NetworkType.UNMETERED
            NetworkPolicy.ANY -> NetworkType.CONNECTED
        }
        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(schedule.chargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    private fun initialDelayMinutes(schedule: ScheduleSettings): Long {
        if (schedule.frequency == ScheduleFrequency.HOURLY) return 0
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, schedule.hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return (next.timeInMillis - now.timeInMillis) / 60000
    }
}
