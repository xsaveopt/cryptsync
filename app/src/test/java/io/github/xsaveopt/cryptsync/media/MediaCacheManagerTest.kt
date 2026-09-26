package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.CacheStatus
import io.github.xsaveopt.cryptsync.data.db.MediaCacheEntity
import io.github.xsaveopt.cryptsync.data.db.MediaType
import io.github.xsaveopt.cryptsync.data.settings.CompressionSettings
import io.github.xsaveopt.cryptsync.testutil.FakeLogDao
import io.github.xsaveopt.cryptsync.testutil.FakeMediaCacheDao
import io.github.xsaveopt.cryptsync.testutil.TestContext
import io.github.xsaveopt.cryptsync.util.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaCacheManagerTest {

    private lateinit var root: File
    private lateinit var source: File
    private lateinit var context: TestContext
    private lateinit var dao: FakeMediaCacheDao
    private lateinit var manager: MediaCacheManager

    private val settings = CompressionSettings()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("media-cache").toFile()
        source = File(root, "source").apply { mkdirs() }
        context = TestContext(root)
        dao = FakeMediaCacheDao()
        manager = MediaCacheManager(
            context,
            SourceScanner(),
            VideoTranscoder(context),
            ImageCompressor(),
            dao,
            AppLogger(FakeLogDao()),
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun sourceFile(name: String, body: String = "data"): File =
        File(source, name).apply { writeText(body) }

    private fun readyEntry(file: File, compressed: File, signature: String = EncodeSignature.of(MediaType.IMAGE, settings)) =
        MediaCacheEntity(
            sourceId = file.absolutePath,
            sourcePath = file.absolutePath,
            sourceSize = file.length(),
            sourceDateModified = file.lastModified(),
            mediaType = MediaType.IMAGE,
            compressedPath = compressed.absolutePath,
            compressedSize = compressed.length(),
            status = CacheStatus.READY,
            encodeSignature = signature,
            updatedAt = 0,
        )

    private fun compressedFor(name: String): File =
        File(context.external, "media_cache/${name}.heic").apply {
            parentFile?.mkdirs()
            writeText("compressed")
        }

    private suspend fun prepare(s: CompressionSettings = settings): List<File> =
        manager.prepare(setOf(source.absolutePath), s)

    @Test
    fun reencodeOffBacksUpEverythingAsIsWithoutCompressing() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val clip = sourceFile("clip.mp4")
        val doc = sourceFile("notes.txt")
        val result = prepare(settings.copy(reencodeMedia = false))
        assertEquals(setOf(photo, clip, doc), result.toSet())
        assertTrue(dao.upserts.isEmpty())
    }

    @Test
    fun encodeFailureFallsBackToOriginalAndMarksFailed() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val progress = mutableListOf<CacheProgress>()
        val result = manager.prepare(setOf(source.absolutePath), settings) { progress.add(it) }
        assertEquals(listOf(photo), result)
        assertEquals(
            listOf(CacheStatus.COMPRESSING, CacheStatus.FAILED),
            dao.upserts.map { it.status },
        )
        val row = dao.rows.getValue(photo.absolutePath)
        assertEquals(EncodeSignature.of(MediaType.IMAGE, settings), row.encodeSignature)
        assertFalse(File(row.compressedPath!!).exists())
        assertEquals(listOf(CacheProgress(0, 1, "photo.jpg")), progress)
    }

    @Test
    fun videoEncodeFailureFallsBackToOriginal() = runBlocking {
        val clip = sourceFile("clip.mp4")
        val result = prepare()
        assertEquals(listOf(clip), result)
        assertEquals(CacheStatus.FAILED, dao.rows.getValue(clip.absolutePath).status)
        assertTrue(dao.rows.getValue(clip.absolutePath).compressedPath!!.endsWith(".mp4"))
    }

    @Test
    fun upToDateEntryIsReusedWithoutCompressing() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed)
        val result = prepare()
        assertEquals(listOf(compressed), result)
        assertTrue(dao.upserts.isEmpty())
    }

    @Test
    fun changedSizeInvalidatesEntry() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed).copy(sourceSize = photo.length() + 1)
        val result = prepare()
        assertEquals(listOf(photo), result)
        assertEquals(CacheStatus.COMPRESSING, dao.upserts.first().status)
    }

    @Test
    fun changedModifiedTimeInvalidatesEntry() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed).copy(sourceDateModified = photo.lastModified() - 1000)
        prepare()
        assertTrue(dao.upserts.isNotEmpty())
    }

    @Test
    fun changedEncodeSettingsInvalidateEntry() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed)
        prepare(settings.copy(imageQuality = settings.imageQuality + 10))
        assertTrue(dao.upserts.isNotEmpty())
    }

    @Test
    fun failedEntryIsRetried() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed).copy(status = CacheStatus.FAILED)
        val result = prepare()
        assertTrue(dao.upserts.isNotEmpty())
        assertEquals(listOf(photo), result)
    }

    @Test
    fun readyEntryWithMissingCompressedFileFallsBackToOriginal() = runBlocking {
        val photo = sourceFile("photo.jpg")
        val compressed = compressedFor("photo")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressed)
        compressed.delete()
        assertEquals(listOf(photo), prepare())
    }

    @Test
    fun deletedSourcesArePurgedFromCache() = runBlocking {
        val kept = sourceFile("kept.jpg")
        val keptCompressed = compressedFor("kept")
        dao.rows[kept.absolutePath] = readyEntry(kept, keptCompressed)
        val gone = File(source, "gone.jpg")
        val goneCompressed = compressedFor("gone")
        dao.rows[gone.absolutePath] = readyEntry(kept, goneCompressed).copy(
            sourceId = gone.absolutePath,
            sourcePath = gone.absolutePath,
        )
        val result = prepare()
        assertEquals(listOf(gone.absolutePath), dao.deletedIds)
        assertFalse(goneCompressed.exists())
        assertTrue(keptCompressed.exists())
        assertEquals(listOf(keptCompressed), result)
    }

    @Test
    fun nothingPurgedWhenAllSourcesPresent() = runBlocking {
        val photo = sourceFile("photo.jpg")
        dao.rows[photo.absolutePath] = readyEntry(photo, compressedFor("photo"))
        prepare()
        assertTrue(dao.deletedIds.isEmpty())
    }

    @Test
    fun nonMediaFilesAreBackedUpAsIs() = runBlocking {
        val doc = sourceFile("notes.txt")
        val result = prepare()
        assertEquals(listOf(doc), result)
        assertTrue(dao.upserts.isEmpty())
    }
}
