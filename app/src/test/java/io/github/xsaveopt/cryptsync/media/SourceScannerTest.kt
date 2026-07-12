package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SourceScannerTest {

    @Test
    fun walksEverythingClassifiesAndHonorsExclusions() = runBlocking {
        val root = Files.createTempDirectory("scan").toFile()
        try {
            File(root, "photo.jpg").writeText("x")
            File(root, "doc.pdf").writeText("x")

            val thumbs = File(root, ".thumbnails").apply { mkdirs() }
            File(thumbs, "thumb.jpg").writeText("x")

            val cache = File(root, "cache").apply { mkdirs() }
            File(cache, ".nomedia").writeText("")
            File(cache, "hidden.jpg").writeText("x")

            val result = SourceScanner().scan(setOf(root.absolutePath))
            val names = result.map { File(it.path).name }.toSet()

            assertTrue("photo.jpg" in names)
            assertTrue("doc.pdf" in names)
            assertFalse("thumb.jpg" in names)
            assertFalse("hidden.jpg" in names)

            assertEquals(MediaType.IMAGE, result.first { File(it.path).name == "photo.jpg" }.type)
            assertNull(result.first { File(it.path).name == "doc.pdf" }.type)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingLocationYieldsNothing() = runBlocking {
        assertTrue(SourceScanner().scan(setOf("/no/such/path/here")).isEmpty())
    }
}
