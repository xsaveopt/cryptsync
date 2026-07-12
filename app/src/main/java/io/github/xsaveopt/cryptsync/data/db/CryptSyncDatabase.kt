package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.name
    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
    @TypeConverter fun cacheStatusToString(value: CacheStatus): String = value.name
    @TypeConverter fun stringToCacheStatus(value: String): CacheStatus = CacheStatus.valueOf(value)
    @TypeConverter fun logLevelToString(value: LogLevel): String = value.name
    @TypeConverter fun stringToLogLevel(value: String): LogLevel = LogLevel.valueOf(value)
    @TypeConverter fun activityOutcomeToString(value: ActivityOutcome): String = value.name
    @TypeConverter fun stringToActivityOutcome(value: String): ActivityOutcome = ActivityOutcome.valueOf(value)
}

@Database(
    entities = [MediaCacheEntity::class, LogEntity::class, ActivityEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CryptSyncDatabase : RoomDatabase() {
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun logDao(): LogDao
    abstract fun activityDao(): ActivityDao
}
