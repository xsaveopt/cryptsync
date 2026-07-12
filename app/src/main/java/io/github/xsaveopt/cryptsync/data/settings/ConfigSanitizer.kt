package io.github.xsaveopt.cryptsync.data.settings

object ConfigSanitizer {
    fun isSafePath(path: String): Boolean =
        path.startsWith("/") &&
            path.length <= 4096 &&
            path.none { it.isISOControl() }

    inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
            ?: default
}
