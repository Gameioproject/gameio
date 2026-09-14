package com.nendo.argosy.data.addon

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonDownloadStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val storage = AddonDownloadStorage()

    @Test fun `same filename in two games cannot share completed or partial bytes`() {
        val platform = temporary.newFolder()
        val local = File(platform, "Game.zip").apply { writeText("local game") }
        val first = storage.directory(platform, 1, "same snapshot")
        val second = storage.directory(platform, 2, "same snapshot")
        File(first, "Game.zip.tmp").writeText("first game")
        File(second, "Game.zip.tmp").writeText("second game")
        assertNotEquals(first, second)
        assertFalse(File(first, local.name).exists())
        assertEquals("local game", local.readText())
        assertEquals("first game", File(first, "Game.zip.tmp").readText())
        assertEquals("second game", File(second, "Game.zip.tmp").readText())
    }

    @Test fun `source snapshot changes isolate partials even without a digest`() {
        val platform = temporary.newFolder()
        val first = storage.directory(platform, 1, "source one without digest")
        File(first, "Game.zip.tmp").writeText("first edition")
        val second = storage.directory(platform, 1, "source two without digest")
        assertFalse(File(second, "Game.zip.tmp").exists())
        assertEquals(first, AddonDownloadStorage().directory(platform, 1, "source one without digest"))
        assertEquals("first edition", File(first, "Game.zip.tmp").readText())
    }

    @Test fun `an unmarked preexisting destination cannot become owned`() {
        val platform = temporary.newFolder()
        val expected = storage.directory(platform, 1, "snapshot", create = false)
        expected.mkdirs()
        val local = File(expected, "Game.zip").apply { writeText("keep") }
        rejected { storage.directory(platform, 1, "snapshot") }
        assertEquals("keep", local.readText())
    }

    @Test fun `foreign marker cannot be replaced during recovery`() {
        val platform = temporary.newFolder()
        val directory = storage.directory(platform, 1, "snapshot")
        val marker = File(directory.parentFile, ".owner").apply { writeText("foreign owner") }
        rejected { storage.directory(platform, 1, "snapshot") }
        assertEquals("foreign owner", marker.readText())
    }

    @Test fun `symlink cannot redirect an owned destination or file to a local game`() {
        val platform = temporary.newFolder()
        val local = temporary.newFile().apply { writeText("keep") }
        val directory = storage.directory(platform, 1, "snapshot")
        val redirected = File(directory, "Game.zip")
        Files.createSymbolicLink(redirected.toPath(), local.toPath())
        rejected { storage.requireContained(directory, redirected) }
        redirected.delete()
        directory.delete()
        Files.createSymbolicLink(directory.toPath(), platform.toPath())
        rejected { storage.directory(platform, 1, "snapshot") }
        assertEquals("keep", local.readText())
    }

    @Test fun `staged destination from another source is rejected`() {
        val platform = temporary.newFolder()
        val first = storage.directory(platform, 1, "first")
        val second = storage.directory(platform, 1, "second")
        rejected { storage.requireDestination(first, second) }
        storage.requireDestination(first, first)
    }

    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected an ownership failure") }
        catch (e: AddonException) { assertEquals(AddonFailure.STORAGE, e.reason) }
    }
}
