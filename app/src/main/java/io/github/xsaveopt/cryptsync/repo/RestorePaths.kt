package io.github.xsaveopt.cryptsync.repo

object RestorePaths {
    const val CONFIG_NAME = "cryptsync-config.json"

    fun mediaRelative(original: String, cachePrefix: String, externalRootRel: String): String? {
        if (!original.startsWith(cachePrefix)) return null
        val mediaAbsolute = original.removePrefix(cachePrefix)
        if (!mediaAbsolute.startsWith(externalRootRel)) return null
        return mediaAbsolute.removePrefix(externalRootRel)
    }

    fun appFolderRelative(
        original: String,
        cachePrefix: String,
        externalRootRel: String,
        isConfig: Boolean,
    ): String = when {
        isConfig -> CONFIG_NAME
        original.startsWith(cachePrefix) -> {
            val mediaAbsolute = original.removePrefix(cachePrefix)
            if (mediaAbsolute.startsWith(externalRootRel)) {
                mediaAbsolute.removePrefix(externalRootRel)
            } else {
                mediaAbsolute.trimStart('/')
            }
        }
        else -> original.trimStart('/')
    }
}
