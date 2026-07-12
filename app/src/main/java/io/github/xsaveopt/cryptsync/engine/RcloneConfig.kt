package io.github.xsaveopt.cryptsync.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RcloneConfig @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val remoteName: String = "gdrive"

    private val configFile: File
        get() = File(context.filesDir, "rclone.conf")

    fun path(): File = configFile

    fun isConfigured(): Boolean = configFile.exists()

    fun writeDrive(tokenJson: String) {
        write(RcloneConfigFormat.drive(remoteName, tokenJson))
    }

    fun writeRawRemote(body: String) {
        write(RcloneConfigFormat.rawRemote(remoteName, body))
    }

    fun clear() {
        configFile.delete()
    }

    private fun write(contents: String) {
        configFile.writeText(contents)
        configFile.setReadable(false, false)
        configFile.setReadable(true, true)
    }
}
