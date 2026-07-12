package io.github.xsaveopt.cryptsync.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestorePathsTest {

    private val cachePrefix =
        "/storage/emulated/0/Android/data/io.github.xsaveopt.cryptsync/files/media_cache/"
    private val externalRootRel = "storage/emulated/0/"

    private fun cached(rel: String) = cachePrefix + rel

    @Test
    fun mediaRelativeStripsCacheAndExternalRoot() {
        val original = cached("storage/emulated/0/DCIM/Camera/foo.heic")
        assertEquals("DCIM/Camera/foo.heic", RestorePaths.mediaRelative(original, cachePrefix, externalRootRel))
    }

    @Test
    fun mediaRelativeReturnsNullForNonCacheFile() {
        val original = "/storage/emulated/0/Download/doc.pdf"
        assertNull(RestorePaths.mediaRelative(original, cachePrefix, externalRootRel))
    }

    @Test
    fun mediaRelativeReturnsNullWhenCachedButNotUnderExternalRoot() {
        val original = cached("data/local/weird.bin")
        assertNull(RestorePaths.mediaRelative(original, cachePrefix, externalRootRel))
    }

    @Test
    fun appFolderRelativeReturnsConfigNameForConfig() {
        assertEquals(
            RestorePaths.CONFIG_NAME,
            RestorePaths.appFolderRelative("/whatever/path", cachePrefix, externalRootRel, isConfig = true),
        )
    }

    @Test
    fun appFolderRelativeLiftsMediaToRealPath() {
        val original = cached("storage/emulated/0/Pictures/a.jpg")
        assertEquals(
            "Pictures/a.jpg",
            RestorePaths.appFolderRelative(original, cachePrefix, externalRootRel, isConfig = false),
        )
    }

    @Test
    fun appFolderRelativeKeepsTreeForOtherFiles() {
        val original = "/storage/emulated/0/Download/doc.pdf"
        assertEquals(
            "storage/emulated/0/Download/doc.pdf",
            RestorePaths.appFolderRelative(original, cachePrefix, externalRootRel, isConfig = false),
        )
    }
}
