package io.github.xsaveopt.cryptsync.util

import io.github.xsaveopt.cryptsync.data.db.ActivityDao
import io.github.xsaveopt.cryptsync.data.db.ActivityEntity
import io.github.xsaveopt.cryptsync.data.db.ActivityOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRecorder @Inject constructor(
    private val activityDao: ActivityDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(title: String, outcome: ActivityOutcome, detail: String = "") {
        scope.launch {
            activityDao.insert(
                ActivityEntity(
                    timestamp = System.currentTimeMillis(),
                    title = title,
                    outcome = outcome,
                    detail = detail,
                ),
            )
            activityDao.prune(MAX_ENTRIES)
        }
    }

    fun recent(): Flow<List<ActivityEntity>> = activityDao.observeRecent(MAX_ENTRIES)

    suspend fun clear() = activityDao.clear()

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
