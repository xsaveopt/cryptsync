package io.github.xsaveopt.cryptsync.util

import io.github.xsaveopt.cryptsync.data.db.LogDao
import io.github.xsaveopt.cryptsync.data.db.LogEntity
import io.github.xsaveopt.cryptsync.data.db.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val logDao: LogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun info(tag: String, message: String) = write(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = write(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = write(LogLevel.ERROR, tag, message)

    fun recent(): Flow<List<LogEntity>> = logDao.observeRecent(MAX_ENTRIES)

    suspend fun clear() = logDao.clear()

    private fun write(level: LogLevel, tag: String, message: String) {
        scope.launch {
            logDao.insert(
                LogEntity(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message,
                ),
            )
            logDao.prune(MAX_ENTRIES)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
