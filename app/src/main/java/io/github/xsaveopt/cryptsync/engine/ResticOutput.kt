package io.github.xsaveopt.cryptsync.engine

import org.json.JSONArray
import org.json.JSONObject

object ResticOutput {
    fun parseSnapshots(output: List<String>): List<Snapshot> {
        val json = output.firstOrNull { it.trimStart().startsWith("[") } ?: "[]"
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pathsArray = obj.optJSONArray("paths")
                val paths = buildList {
                    if (pathsArray != null) {
                        for (p in 0 until pathsArray.length()) add(pathsArray.getString(p))
                    }
                }
                add(
                    Snapshot(
                        id = obj.getString("short_id"),
                        time = obj.getString("time"),
                        paths = paths,
                        hostname = obj.optString("hostname"),
                    ),
                )
            }
        }
    }

    fun parseKeys(output: List<String>): List<RepoKey> {
        val json = output.firstOrNull { it.trimStart().startsWith("[") } ?: "[]"
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    RepoKey(
                        id = obj.getString("id"),
                        userName = obj.optString("userName"),
                        hostName = obj.optString("hostName"),
                        created = obj.optString("created"),
                        current = obj.optBoolean("current"),
                    ),
                )
            }
        }
    }

    fun restoreErrorItem(line: String): String? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val obj = JSONObject(trimmed)
            if (obj.optString("message_type") != "error") return@runCatching null
            obj.optString("item").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun lsPaths(output: List<String>): List<String> = buildList {
        for (line in output) {
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("{")) continue
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: continue
            val path = obj.optString("path", "")
            if (path.isNotEmpty() && obj.has("type")) add(path)
        }
    }

    fun backupAddedBytes(output: List<String>): Long? {
        for (line in output.asReversed()) {
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("{")) continue
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: continue
            if (obj.optString("message_type") != "summary") continue
            val packed = obj.optLong("data_added_packed", -1L)
            if (packed >= 0) return packed
            return obj.optLong("data_added", -1L).takeIf { it >= 0 }
        }
        return null
    }

    fun rawDataSize(output: List<String>): Long? {
        for (line in output) {
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("{")) continue
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: continue
            val size = obj.optLong("total_size", -1L)
            if (size >= 0) return size
        }
        return null
    }

    fun backupPercent(line: String): Int? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val obj = JSONObject(trimmed)
            if (obj.optString("message_type") != "status") return@runCatching null
            (obj.optDouble("percent_done", -1.0) * 100).toInt().takeIf { it in 0..100 }
        }.getOrNull()
    }
}
