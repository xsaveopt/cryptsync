package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogLevel { INFO, WARN, ERROR }

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
