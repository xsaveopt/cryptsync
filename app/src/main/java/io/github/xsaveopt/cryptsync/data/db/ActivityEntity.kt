package io.github.xsaveopt.cryptsync.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityOutcome { SUCCESS, FAILURE, INFO }

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val title: String,
    val outcome: ActivityOutcome,
    val detail: String,
)
