package io.github.xsaveopt.cryptsync.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatSnapshotTime(iso: String): String {
    val pattern = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.getDefault())
    return runCatching { OffsetDateTime.parse(iso).format(pattern) }
        .recoverCatching { LocalDateTime.parse(iso).format(pattern) }
        .getOrDefault(iso)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
