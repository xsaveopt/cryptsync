package io.github.xsaveopt.cryptsync.repo

import org.json.JSONObject

object RestoreDirs {
    fun backupLocationsFromConfig(json: String): List<String> = runCatching {
        val array = JSONObject(json).optJSONArray("backupLocations") ?: return emptyList()
        buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    }.getOrDefault(emptyList())

    fun topLevelDirs(paths: List<String>): List<String> {
        val dirs = paths
            .map { it.substringBeforeLast('/', "") }
            .filter { it.isNotEmpty() }
            .toSortedSet()
        return dirs.filter { dir -> dirs.none { other -> other != dir && dir.startsWith("$other/") } }
    }
}
