package io.github.xsaveopt.cryptsync.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import java.io.File

class TestContext(root: File) : ContextWrapper(null) {
    val files: File = File(root, "files").apply { mkdirs() }
    val cache: File = File(root, "cache").apply { mkdirs() }
    val external: File = File(root, "external").apply { mkdirs() }
    val nativeLibs: File = File(root, "lib").apply { mkdirs() }

    override fun getFilesDir(): File = files

    override fun getCacheDir(): File = cache

    override fun getExternalFilesDir(type: String?): File = external

    override fun getApplicationInfo(): ApplicationInfo =
        ApplicationInfo().apply { nativeLibraryDir = nativeLibs.absolutePath }

    override fun getApplicationContext(): Context = this
}
