package io.github.xsaveopt.cryptsync.media

import io.github.xsaveopt.cryptsync.data.db.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileClassifierTest {

    @Test
    fun classifiesImages() {
        assertEquals(MediaType.IMAGE, FileClassifier.classify("holiday.jpg"))
        assertEquals(MediaType.IMAGE, FileClassifier.classify("shot.heic"))
        assertEquals(MediaType.IMAGE, FileClassifier.classify("art.PNG"))
    }

    @Test
    fun classifiesVideos() {
        assertEquals(MediaType.VIDEO, FileClassifier.classify("clip.mp4"))
        assertEquals(MediaType.VIDEO, FileClassifier.classify("movie.MKV"))
    }

    @Test
    fun nonMediaReturnsNull() {
        assertNull(FileClassifier.classify("report.pdf"))
        assertNull(FileClassifier.classify("archive.zip"))
        assertNull(FileClassifier.classify("msgstore.db"))
        assertNull(FileClassifier.classify("noextension"))
    }

    @Test
    fun rawAndGifStayNonMediaToAvoidLossyReencode() {
        assertNull(FileClassifier.classify("photo.dng"))
        assertNull(FileClassifier.classify("animation.gif"))
    }
}
