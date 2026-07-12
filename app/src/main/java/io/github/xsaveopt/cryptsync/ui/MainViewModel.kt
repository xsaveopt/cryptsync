package io.github.xsaveopt.cryptsync.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xsaveopt.cryptsync.R
import io.github.xsaveopt.cryptsync.backup.BackupProgress
import io.github.xsaveopt.cryptsync.backup.BackupScheduler
import io.github.xsaveopt.cryptsync.data.settings.AppSettings
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.data.settings.ScheduleSettings
import io.github.xsaveopt.cryptsync.data.settings.SettingsRepository
import io.github.xsaveopt.cryptsync.engine.DriveQuota
import io.github.xsaveopt.cryptsync.engine.RcloneConfig
import io.github.xsaveopt.cryptsync.engine.RcloneEngine
import io.github.xsaveopt.cryptsync.engine.RepoKey
import io.github.xsaveopt.cryptsync.engine.Snapshot
import io.github.xsaveopt.cryptsync.data.db.ActivityEntity
import io.github.xsaveopt.cryptsync.data.db.ActivityOutcome
import io.github.xsaveopt.cryptsync.data.db.LogEntity
import io.github.xsaveopt.cryptsync.util.ActivityRecorder
import io.github.xsaveopt.cryptsync.util.AppLogger
import io.github.xsaveopt.cryptsync.util.friendlyMessage
import io.github.xsaveopt.cryptsync.media.MediaCacheManager
import io.github.xsaveopt.cryptsync.repo.IntegrityResult
import io.github.xsaveopt.cryptsync.repo.RepositoryManager
import io.github.xsaveopt.cryptsync.repo.RestoreManager
import io.github.xsaveopt.cryptsync.repo.SetupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RepoSize {
    data object Loading : RepoSize
    data object Unavailable : RepoSize
    data class Known(val bytes: Long) : RepoSize
}

data class RestorePrompt(
    val snapshotId: String,
    val directories: List<String>,
    val duringSetup: Boolean,
)

data class UiState(
    val setupState: SetupState = SetupState.NeedsCloud,
    val compressedBytes: Long = 0,
    val snapshots: List<Snapshot> = emptyList(),
    val keys: List<RepoKey> = emptyList(),
    val quota: DriveQuota? = null,
    val integrity: IntegrityResult? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repositoryManager: RepositoryManager,
    private val restoreManager: RestoreManager,
    private val scheduler: BackupScheduler,
    private val rcloneConfig: RcloneConfig,
    private val rcloneEngine: RcloneEngine,
    private val logger: AppLogger,
    private val activityRecorder: ActivityRecorder,
    private val mediaCacheManager: MediaCacheManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val compressedBytes: StateFlow<Long> = mediaCacheManager.compressedBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _repoSize = MutableStateFlow<RepoSize>(RepoSize.Loading)
    val repoSize: StateFlow<RepoSize> = _repoSize.asStateFlow()

    private val _restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    val restorePrompt: StateFlow<RestorePrompt?> = _restorePrompt.asStateFlow()

    val logs: StateFlow<List<LogEntity>> = logger.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activity: StateFlow<List<ActivityEntity>> = activityRecorder.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupProgress: StateFlow<BackupProgress> = scheduler.progress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupProgress(false, -1))

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshSetupState()
        migrateOnboardingFlag()
    }

    fun refreshSetupState() = launchBusy {
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
    }

    private fun migrateOnboardingFlag() = viewModelScope.launch {
        val alreadyReady = repositoryManager.setupState() == SetupState.Ready
        if (alreadyReady && !settingsRepository.settings.first().onboardingComplete) {
            settingsRepository.setOnboardingComplete(true)
        }
    }

    fun connectDrive(tokenJson: String) = launchBusy {
        rcloneConfig.writeDrive(tokenJson)
        logger.info("Drive", "Google Drive connected")
        message(R.string.msg_drive_connected)
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
    }

    fun connectDebugBackend(configBody: String) = launchBusy {
        rcloneConfig.writeRawRemote(configBody)
        logger.info("Drive", "Connected a custom rclone backend for testing")
        message(R.string.msg_drive_connected)
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
    }

    fun createRepository(password: String) = launchBusy {
        repositoryManager.createRepository(password)
        message(R.string.msg_repository_ready)
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
    }

    fun changePassword(current: String, new: String) = launchBusy(R.string.activity_action_password) {
        repositoryManager.changePassword(current, new)
        _uiState.value = _uiState.value.copy(keys = repositoryManager.listKeys())
        recordActivity(
            R.string.activity_action_password,
            ActivityOutcome.SUCCESS,
            context.getString(R.string.activity_password_detail),
        )
        message(R.string.msg_password_changed)
    }

    fun loadKeys() = launchBusy {
        _uiState.value = _uiState.value.copy(keys = repositoryManager.listKeys())
    }

    fun removeKey(id: String) = launchBusy(R.string.activity_action_key_removed) {
        repositoryManager.removeKey(id)
        _uiState.value = _uiState.value.copy(keys = repositoryManager.listKeys())
        recordActivity(
            R.string.activity_action_key_removed,
            ActivityOutcome.SUCCESS,
            context.getString(R.string.activity_key_removed_detail, id.take(8)),
        )
    }

    fun checkIntegrity() = launchBusy(R.string.activity_action_integrity) {
        val result = repositoryManager.checkIntegrity()
        _uiState.value = _uiState.value.copy(integrity = result)
        val detail = if (result.healthy) {
            context.getString(R.string.check_healthy)
        } else {
            context.getString(R.string.check_problems, result.affectedFiles.size)
        }
        recordActivity(
            R.string.activity_action_integrity,
            if (result.healthy) ActivityOutcome.SUCCESS else ActivityOutcome.FAILURE,
            detail,
        )
        message(detail)
    }

    fun repairAndBackup() = launchBusy(R.string.activity_action_repair) {
        if (repositoryManager.repairRepository()) {
            _uiState.value = _uiState.value.copy(integrity = null)
            scheduler.runNow()
            recordActivity(
                R.string.activity_action_repair,
                ActivityOutcome.SUCCESS,
                context.getString(R.string.check_repair_started),
            )
            message(R.string.check_repair_started)
        } else {
            recordActivity(
                R.string.activity_action_repair,
                ActivityOutcome.FAILURE,
                context.getString(R.string.check_repair_failed),
            )
            message(R.string.check_repair_failed)
        }
    }

    fun runBackupNow() {
        scheduler.runNow()
        logger.info("Backup", "Backup requested from the app")
        recordActivity(
            R.string.activity_action_backup,
            ActivityOutcome.INFO,
            context.getString(R.string.activity_backup_requested),
        )
        message(R.string.msg_backup_started)
    }

    fun cancelBackup() {
        scheduler.cancelRunning()
        logger.info("Backup", "Backup cancelled by the user")
        message(R.string.msg_backup_cancelled)
    }

    fun disconnectDrive() = launchBusy {
        rcloneConfig.clear()
        logger.info("Drive", "Google Drive disconnected")
        message(R.string.msg_drive_disconnected)
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
    }

    fun updateCompression(settings: CompressionSettings) = launchBusy {
        settingsRepository.setCompression(settings)
    }

    fun updateSchedule(schedule: ScheduleSettings) = launchBusy {
        settingsRepository.setSchedule(schedule)
        scheduler.apply(schedule)
    }

    fun setBackupLocations(locations: Set<String>) = launchBusy {
        settingsRepository.setBackupLocations(locations)
    }

    fun updateStorageLimit(gb: Int) = launchBusy {
        settingsRepository.setStorageLimit(gb)
    }

    fun loadRepoSize() = viewModelScope.launch {
        _repoSize.value = RepoSize.Loading
        val bytes = repositoryManager.repoSizeBytes()
        _repoSize.value = if (bytes != null) RepoSize.Known(bytes) else RepoSize.Unavailable
    }

    fun loadSnapshots() = launchBusy {
        _uiState.value = _uiState.value.copy(snapshots = restoreManager.listSnapshots())
    }

    fun completeOnboarding() = launchBusy {
        settingsRepository.setOnboardingComplete(true)
    }

    fun unlockBackupForRestore(password: String) = launchBusy {
        if (repositoryManager.unlockExistingRepository(password)) {
            _uiState.value = _uiState.value.copy(snapshots = restoreManager.listSnapshots())
            message(context.getString(R.string.setup_backup_unlocked))
        } else {
            message(context.getString(R.string.setup_wrong_password))
        }
    }

    fun promptRestoreInPlace(snapshotId: String, duringSetup: Boolean) = launchBusy {
        val directories = restoreManager.backupDirectories(snapshotId)
        _restorePrompt.value = RestorePrompt(snapshotId, directories, duringSetup)
    }

    fun dismissRestorePrompt() {
        _restorePrompt.value = null
    }

    fun confirmRestore() {
        val prompt = _restorePrompt.value ?: return
        _restorePrompt.value = null
        if (prompt.duringSetup) restoreDuringSetup(prompt.snapshotId) else restoreInPlace(prompt.snapshotId)
    }

    fun restoreDuringSetup(snapshotId: String) = launchBusy(R.string.activity_action_restore) {
        val summary = restoreManager.restoreInPlace(snapshotId)
        repositoryManager.markRepositoryReady()
        settingsRepository.setOnboardingComplete(true)
        _uiState.value = _uiState.value.copy(setupState = repositoryManager.setupState())
        val parts = buildList {
            add(context.getString(R.string.msg_restore_media_count, summary.mediaPublished))
            if (summary.mediaAlreadyPresent > 0) {
                add(context.getString(R.string.msg_restore_already_present, summary.mediaAlreadyPresent))
            }
            if (summary.filesWritten > 0) {
                add(context.getString(R.string.msg_restore_files_count, summary.filesWritten))
            }
            if (summary.configRestored) add(context.getString(R.string.setup_config_restored))
            if (summary.lostFiles.isNotEmpty()) {
                add(context.getString(R.string.msg_restore_lost_count, summary.lostFiles.size))
            }
        }
        val detail = parts.joinToString(", ")
        recordActivity(
            R.string.activity_action_restore,
            if (summary.lostFiles.isEmpty()) ActivityOutcome.SUCCESS else ActivityOutcome.INFO,
            detail,
        )
        message(detail)
    }

    fun loadQuota() = launchBusy {
        _uiState.value = _uiState.value.copy(quota = rcloneEngine.about())
    }

    fun restore(snapshotId: String) = launchBusy(R.string.activity_action_restore) {
        val outcome = restoreManager.restore(snapshotId)
        val parts = buildList {
            add(context.getString(R.string.msg_restored_to, outcome.target.absolutePath))
            if (outcome.lostFiles.isNotEmpty()) {
                add(context.getString(R.string.msg_restore_lost_count, outcome.lostFiles.size))
            }
        }
        val detail = parts.joinToString(". ")
        recordActivity(
            R.string.activity_action_restore,
            if (outcome.lostFiles.isEmpty()) ActivityOutcome.SUCCESS else ActivityOutcome.INFO,
            detail,
        )
        message(detail)
    }

    fun restoreInPlace(snapshotId: String) = launchBusy(R.string.activity_action_restore) {
        val summary = restoreManager.restoreInPlace(snapshotId)
        val detail = restoreSummaryMessage(summary)
        recordActivity(
            R.string.activity_action_restore,
            if (summary.lostFiles.isEmpty()) ActivityOutcome.SUCCESS else ActivityOutcome.INFO,
            detail,
        )
        message(detail)
    }

    private fun restoreSummaryMessage(summary: io.github.xsaveopt.cryptsync.repo.RestoreSummary): String {
        val parts = buildList {
            add(context.getString(R.string.msg_restore_media_count, summary.mediaPublished))
            if (summary.mediaAlreadyPresent > 0) {
                add(context.getString(R.string.msg_restore_already_present, summary.mediaAlreadyPresent))
            }
            if (summary.filesWritten > 0) {
                add(context.getString(R.string.msg_restore_files_count, summary.filesWritten))
            }
            if (summary.skippedNoAccess > 0) {
                add(context.getString(R.string.msg_restore_skipped_count, summary.skippedNoAccess))
            }
            if (summary.mediaFailed > 0) {
                add(context.getString(R.string.msg_restore_media_failed, summary.mediaFailed))
            }
            if (summary.lostFiles.isNotEmpty()) {
                add(context.getString(R.string.msg_restore_lost_count, summary.lostFiles.size))
            }
        }
        return parts.joinToString(", ")
    }

    fun clearLogs() = viewModelScope.launch { logger.clear() }

    fun clearActivity() = viewModelScope.launch { activityRecorder.clear() }

    private fun recordActivity(@StringRes title: Int, outcome: ActivityOutcome, detail: String = "") {
        activityRecorder.record(context.getString(title), outcome, detail)
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun message(text: String) {
        _uiState.value = _uiState.value.copy(message = text)
    }

    private fun message(@StringRes resId: Int) {
        message(context.getString(resId))
    }

    private fun launchBusy(@StringRes activityTitle: Int? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            try {
                block()
            } catch (e: Exception) {
                logger.error("App", e.message ?: e.javaClass.simpleName)
                activityTitle?.let { recordActivity(it, ActivityOutcome.FAILURE, friendlyMessage(e)) }
                message(friendlyMessage(e))
            } finally {
                _uiState.value = _uiState.value.copy(busy = false)
            }
        }
    }
}
