package io.github.xsaveopt.cryptsync.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreDirsTest {

    @Test
    fun readsBackupLocationsFromConfig() {
        val json = """{"backupLocations":["/storage/emulated/0/DCIM","/storage/emulated/0/Download"]}"""
        assertEquals(
            listOf("/storage/emulated/0/DCIM", "/storage/emulated/0/Download"),
            RestoreDirs.backupLocationsFromConfig(json),
        )
    }

    @Test
    fun backupLocationsEmptyOnBadJson() {
        assertTrue(RestoreDirs.backupLocationsFromConfig("not json").isEmpty())
        assertTrue(RestoreDirs.backupLocationsFromConfig("{}").isEmpty())
    }

    @Test
    fun topLevelDirsCollapsesNestedFolders() {
        val paths = listOf(
            "/storage/emulated/0/DCIM/Camera/a.jpg",
            "/storage/emulated/0/DCIM/Camera/sub/b.jpg",
            "/storage/emulated/0/Download/c.pdf",
        )
        assertEquals(
            listOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Download"),
            RestoreDirs.topLevelDirs(paths),
        )
    }
}
