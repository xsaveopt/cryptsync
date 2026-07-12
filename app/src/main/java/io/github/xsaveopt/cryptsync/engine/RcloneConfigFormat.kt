package io.github.xsaveopt.cryptsync.engine

object RcloneConfigFormat {
    fun drive(remoteName: String, tokenJson: String): String = buildString {
        appendLine("[$remoteName]")
        appendLine("type = drive")
        appendLine("scope = drive")
        appendLine("token = ${tokenJson.trim()}")
    }

    fun rawRemote(remoteName: String, body: String): String {
        val lines = body.trim().lines().dropWhile { it.isBlank() }
        val withoutHeader = if (lines.firstOrNull()?.trim()?.startsWith("[") == true) {
            lines.drop(1)
        } else {
            lines
        }
        return buildString {
            appendLine("[$remoteName]")
            withoutHeader.forEach { appendLine(it) }
        }
    }
}
