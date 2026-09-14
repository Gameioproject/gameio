package com.nendo.argosy.data.addon

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonFileVerifierTest {
    @get:Rule val folder = TemporaryFolder()
    private val format = AddonFormat(Moshi.Builder().build())
    private val verifier = AddonFileVerifier(format)
    private val source = AddonSource("one", "http", "Game.rom", AddonLocator(url = "https://example.org/Game.rom"),
        size = 3, md5 = "900150983cd24fb0d6963f7d28e17f72", sha1 = "a9993e364706816aba3e25717850c26c9cd0d89d")

    @Test fun `known size and digests must all agree before extraction`() = runBlocking {
        val file = folder.newFile().apply { writeText("abc") }
        verifier.verify(file, json(source))
        file.writeText("abd")
        try { verifier.verify(file, json(source)); fail("Expected checksum failure") }
        catch (e: AddonException) { assertEquals(AddonFailure.INTEGRITY, e.reason) }
        assertEquals("abd", file.readText())
    }

    @Test fun `partial or oversized transfer is not promoted`() = runBlocking {
        val file = folder.newFile().apply { writeText("ab") }
        try { verifier.verify(file, json(source.copy(md5 = null, sha1 = null))); fail("Expected size failure") }
        catch (e: AddonException) { assertEquals(AddonFailure.INTEGRITY, e.reason) }
    }

    @Test fun `optional hashes do not block providers with known file size only`() = runBlocking {
        val file = folder.newFile().apply { writeText("abc") }
        verifier.verify(file, json(source.copy(md5 = null, sha1 = null)))
    }

    private fun json(value: AddonSource) = format.encodeMatch(AddonSourceMatch("test", "Test", value, "a".repeat(64), "123:snes"))
}
