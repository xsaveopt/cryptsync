package io.github.xsaveopt.cryptsync.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityParserTest {

    private val packA = "30e8909f9fa3dc595cb2c6dd4c3d2537c3c9f6bdae6ea7904b4a72261d9ac5ed"
    private val packB = "7daae2169b015e93400a576e039c7830be2ecc4d15dbf9e988333245adbff018"

    @Test
    fun findsMissingPackFromCheckOutput() {
        val output = listOf(
            "check all packs",
            "pack $packA: does not exist",
            "check snapshots, trees and blobs",
            "Load(<data/30e8909f9f>, 0, 0) failed: <data/30e8909f9f> does not exist",
            "Fatal: repository contains errors",
        )
        assertEquals(listOf(packA), IntegrityParser.missingPacks(output))
    }

    @Test
    fun deduplicatesRepeatedPackId() {
        val output = listOf(
            "pack $packA: does not exist",
            "another line mentioning $packA does not exist",
        )
        assertEquals(listOf(packA), IntegrityParser.missingPacks(output))
    }

    @Test
    fun findsMultipleDistinctPacks() {
        val output = listOf(
            "pack $packA: does not exist",
            "pack $packB: does not exist",
        )
        assertEquals(listOf(packA, packB), IntegrityParser.missingPacks(output))
    }

    @Test
    fun summaryIsSingularForOnePack() {
        val summary = IntegrityParser.summary(listOf(packA))
        assertEquals("1 data pack missing from the cloud", summary.first())
        assertTrue(summary.contains("Missing pack ${packA.take(12)}"))
    }

    @Test
    fun summaryIsPluralForMultiplePacks() {
        val summary = IntegrityParser.summary(listOf(packA, packB))
        assertEquals("2 data packs missing from the cloud", summary.first())
    }

    @Test
    fun summaryFallsBackWhenNoPacksIdentified() {
        val summary = IntegrityParser.summary(emptyList())
        assertEquals(listOf("The repository reports errors, see the details below"), summary)
    }
}
