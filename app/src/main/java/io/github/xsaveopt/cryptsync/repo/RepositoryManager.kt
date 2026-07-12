package io.github.xsaveopt.cryptsync.repo

import io.github.xsaveopt.cryptsync.crypto.PasswordStore
import io.github.xsaveopt.cryptsync.data.settings.SettingsRepository
import io.github.xsaveopt.cryptsync.engine.RcloneConfig
import io.github.xsaveopt.cryptsync.engine.RepoKey
import io.github.xsaveopt.cryptsync.engine.ResticEngine
import io.github.xsaveopt.cryptsync.util.AppLogger
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SetupState {
    data object NeedsCloud : SetupState
    data object NeedsPassword : SetupState
    data object NeedsRepository : SetupState
    data object Ready : SetupState
}

data class IntegrityResult(
    val healthy: Boolean,
    val missingPackIds: List<String>,
    val details: List<String>,
    val affectedFiles: List<String>,
)

@Singleton
class RepositoryManager @Inject constructor(
    private val passwordStore: PasswordStore,
    private val resticEngine: ResticEngine,
    private val rcloneConfig: RcloneConfig,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
) {
    suspend fun setupState(): SetupState = when {
        !rcloneConfig.isConfigured() -> SetupState.NeedsCloud
        !passwordStore.hasPassword() -> SetupState.NeedsPassword
        !settings.settings.first().repositoryInitialized -> SetupState.NeedsRepository
        else -> SetupState.Ready
    }

    suspend fun createRepository(password: String) {
        check(rcloneConfig.isConfigured()) { "Cloud must be connected before creating the repository" }
        passwordStore.setPassword(password)
        if (resticEngine.isInitialized(password)) {
            logger.info(TAG, "Existing restic repository found, reusing it")
        } else {
            logger.info(TAG, "Initializing new restic repository")
            resticEngine.initRepository(password)
        }
        settings.setRepositoryInitialized(true)
        logger.info(TAG, "Repository ready")
    }

    fun unlock(password: String): Boolean = passwordStore.verify(password)

    fun activePassword(): String? = passwordStore.getPassword()

    suspend fun unlockExistingRepository(password: String): Boolean {
        check(rcloneConfig.isConfigured()) { "Cloud must be connected before restoring" }
        if (!resticEngine.isInitialized(password)) return false
        passwordStore.setPassword(password)
        logger.info(TAG, "Existing backup unlocked for restore")
        return true
    }

    suspend fun markRepositoryReady() {
        settings.setRepositoryInitialized(true)
    }

    suspend fun changePassword(current: String, new: String) {
        require(passwordStore.verify(current)) { "Current password is incorrect" }
        resticEngine.addKey(current, new)
        passwordStore.setPassword(new)
        logger.info(TAG, "Password changed, a new repository key was added")
    }

    suspend fun listKeys(): List<RepoKey> {
        val password = passwordStore.getPassword() ?: error("No active password")
        return resticEngine.listKeys(password)
    }

    suspend fun removeKey(keyId: String) {
        val password = passwordStore.getPassword() ?: error("No active password")
        resticEngine.removeKey(password, keyId)
        logger.info(TAG, "Removed repository key ${keyId.take(8)}")
    }

    suspend fun repoSizeBytes(): Long? {
        val password = passwordStore.getPassword() ?: return null
        return resticEngine.rawDataSize(password)
    }

    suspend fun projectedRepoBytes(paths: List<File>): Long? {
        val password = passwordStore.getPassword() ?: return null
        val current = resticEngine.rawDataSize(password) ?: 0L
        val added = resticEngine.dryRunAddedBytes(password, paths) ?: return null
        return current + added
    }

    suspend fun keepOnlyLatestSnapshot() {
        val password = passwordStore.getPassword() ?: return
        val result = resticEngine.forgetKeepLast(password, keepLast = 1, prune = true)
        if (result.isSuccess) {
            logger.info(TAG, "Pruned old snapshots, keeping only the latest so the repository stays a pure mirror")
        } else {
            logger.warn(TAG, "Prune reported issues: ${result.output.takeLast(5).joinToString(" | ")}")
        }
    }

    suspend fun repairRepository(): Boolean {
        val password = passwordStore.getPassword() ?: error("No active password")
        logger.info(TAG, "Rebuilding the repository index to drop references to missing data")
        val indexResult = resticEngine.repairIndex(password)
        if (!indexResult.isSuccess) {
            logger.error(TAG, "Index repair failed: ${indexResult.output.takeLast(5).joinToString(" | ")}")
            return false
        }
        logger.info(TAG, "Index rebuilt, repairing snapshots that still reference missing data")
        val snapshotResult = resticEngine.repairSnapshots(password)
        if (snapshotResult.isSuccess) {
            logger.info(TAG, "Snapshots repaired, broken ones dropped; the next backup will re-upload any files whose source still exists")
        } else {
            logger.warn(TAG, "Snapshot repair reported issues: ${snapshotResult.output.takeLast(5).joinToString(" | ")}")
        }
        return true
    }

    suspend fun checkIntegrity(): IntegrityResult {
        val password = passwordStore.getPassword() ?: error("No active password")
        logger.info(TAG, "Running integrity check")
        val result = resticEngine.check(password)
        if (result.isSuccess) {
            logger.info(TAG, "Integrity check passed, backup is intact")
            return IntegrityResult(healthy = true, missingPackIds = emptyList(), details = emptyList(), affectedFiles = emptyList())
        }

        val details = result.output.filter { it.isNotBlank() }
        val missingPacks = IntegrityParser.missingPacks(details)

        val files = LinkedHashSet<String>()
        missingPacks.forEach { id ->
            resticEngine.filesInPack(password, id).output.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("/")) files.add(trimmed)
            }
        }

        logger.error(TAG, "Integrity check found problems: ${IntegrityParser.summary(missingPacks).joinToString(" | ")}")
        if (files.isNotEmpty()) {
            logger.error(TAG, "Affected files: ${files.joinToString(", ")}")
        }
        return IntegrityResult(healthy = false, missingPackIds = missingPacks, details = details, affectedFiles = files.toList())
    }

    private companion object {
        const val TAG = "Repository"
    }
}
