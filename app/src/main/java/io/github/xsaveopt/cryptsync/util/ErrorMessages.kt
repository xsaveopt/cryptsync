package io.github.xsaveopt.cryptsync.util

fun friendlyMessage(error: Throwable): String {
    val text = (error.message ?: "").lowercase()
    return when {
        text.contains("storage quota") ||
            text.contains("storagequotaexceeded") ||
            text.contains("insufficient storage") ||
            text.contains("no space") ->
            "Not enough space in your Google Drive. Raise compression or free up Drive space."
        text.contains("unauthorized") || text.contains("invalid_grant") ->
            "Google Drive sign in expired. Reconnect your account."
        error.message.isNullOrBlank() -> "Something went wrong"
        else -> error.message!!
    }
}
