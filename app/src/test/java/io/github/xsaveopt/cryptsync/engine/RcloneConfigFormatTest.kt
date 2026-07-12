package io.github.xsaveopt.cryptsync.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneConfigFormatTest {

    @Test
    fun driveTrimsTokenAndSetsRemoteHeader() {
        val out = RcloneConfigFormat.drive("gdrive", "  {\"access_token\":\"x\"}  ")
        assertTrue(out.contains("[gdrive]"))
        assertTrue(out.contains("type = drive"))
        assertTrue(out.contains("token = {\"access_token\":\"x\"}"))
    }

    @Test
    fun rawRemoteStripsPastedHeaderAndReplacesIt() {
        val body = """
            [minio]
            type = s3
            endpoint = http://10.0.2.2:9000
        """.trimIndent()
        val out = RcloneConfigFormat.rawRemote("gdrive", body)
        assertTrue(out.startsWith("[gdrive]"))
        assertFalse(out.contains("[minio]"))
        assertTrue(out.contains("type = s3"))
        assertTrue(out.contains("endpoint = http://10.0.2.2:9000"))
    }

    @Test
    fun rawRemoteKeepsBodyWithoutHeader() {
        val body = "type = webdav\nurl = http://192.168.1.10:8080"
        val out = RcloneConfigFormat.rawRemote("gdrive", body)
        assertTrue(out.startsWith("[gdrive]"))
        assertTrue(out.contains("type = webdav"))
        assertTrue(out.contains("url = http://192.168.1.10:8080"))
    }

    @Test
    fun rawRemoteDropsLeadingBlankLinesBeforeHeader() {
        val body = "\n\n[remote]\ntype = s3"
        val out = RcloneConfigFormat.rawRemote("gdrive", body)
        assertFalse(out.contains("[remote]"))
        assertTrue(out.contains("type = s3"))
    }
}
