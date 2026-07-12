package io.github.xsaveopt.cryptsync.nativebin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class NativeBinary(val libName: String) {
    RESTIC("librestic.so"),
    RCLONE("librclone.so"),
}

@Singleton
class NativeBinaries @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nativeLibDir: File
        get() = File(context.applicationInfo.nativeLibraryDir)

    fun path(binary: NativeBinary): File {
        val file = File(nativeLibDir, binary.libName)
        check(file.exists()) { "Native binary ${binary.libName} not found at ${file.absolutePath}" }
        return file
    }

    fun isAvailable(binary: NativeBinary): Boolean =
        File(nativeLibDir, binary.libName).exists()
}
