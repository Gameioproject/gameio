package com.nendo.argosy.data.addon

import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AddonFormatTest {
    private val moshi = Moshi.Builder().build()
    private val format = AddonFormat(moshi)
    private val manifest = AddonManifest(1, "test.sources", "Test sources", "1", "catalog-shards-v1",
        AddonLookup("igdbId:platformSlug", "sha256-prefix-2", "https://sources.example/{shard}.json"),
        listOf("sources.example"))
    private val source = AddonSource("source-1", "internet_archive", "Game (USA).zip",
        AddonLocator(item = "test-archive", path = "SNES/Game (USA).zip"), size = 10000)

    @Test fun `manifest import validates without requesting an index`() {
        assertEquals(manifest, format.parseManifest(manifestBytes(manifest)))
        assertEquals("https://sources.example/${AddonFormat.partition("123:snes")}.json",
            format.shardUrl(manifest, "123:snes").toString())
    }

    @Test fun `partition is stable across client and server`() {
        assertEquals("ba", AddonFormat.partition("abc"))
        assertNotEquals(AddonFormat.partition("123:snes"), AddonFormat.partition("123:n64"))
    }

    @Test fun `unknown adapter and schema cannot silently become no source`() {
        assertFailure(AddonFailure.UNSUPPORTED_VERSION) { format.parseManifest(manifestBytes(manifest.copy(adapter = "javascript"))) }
        assertFailure(AddonFailure.UNSUPPORTED_VERSION) { format.parseManifest(manifestBytes(manifest.copy(schemaVersion = 2))) }
    }

    @Test fun `manifest cannot leak credentials or redirect to an undeclared host`() {
        for (url in listOf("http://sources.example/00.json", "https://secret@sources.example/00.json",
            "https://sources.example.evil/00.json", "https://elsewhere.example/00.json")) {
            assertFailure(AddonFailure.UNTRUSTED_HOST) { format.approvedUrl(url, manifest.allowedHosts) }
        }
    }

    @Test fun `template requires exactly one shard placeholder`() {
        for (template in listOf("https://sources.example/all.json", "https://sources.example/{shard}/{shard}.json")) {
            assertFailure(AddonFailure.INVALID_MANIFEST) {
                format.parseManifest(manifestBytes(manifest.copy(lookup = manifest.lookup.copy(urlTemplate = template))))
            }
        }
    }

    @Test fun `valid empty shard is a confirmed miss but invalid shard is an error`() {
        assertTrue(format.parseShard("{\"schemaVersion\":1,\"entries\":{}}".toByteArray(), manifest, "00").entries.isEmpty())
        assertFailure(AddonFailure.INVALID_MANIFEST) { format.parseShard("<html>error</html>".toByteArray(), manifest, "00") }
    }

    @Test fun `every source must belong to its declared shard and platform key`() {
        val key = "123:snes"
        assertEquals(source, parseSources(key, listOf(source)).entries[key]!!.single())
        assertFailure(AddonFailure.INVALID_MANIFEST) {
            format.parseShard(shardBytes(AddonShard(1, mapOf(key to listOf(source)))), manifest,
                if (AddonFormat.partition(key) == "00") "01" else "00")
        }
    }

    @Test fun `unsafe filenames and traversal never reach download storage`() {
        for (name in listOf("../Game.zip", "/Game.zip", "sub\\Game.zip", "Game\u0000.zip", ".", "..")) {
            assertFailure(AddonFailure.INVALID_SOURCE) { parseSources("123:snes", listOf(source.copy(filename = name))) }
        }
        assertFailure(AddonFailure.INVALID_SOURCE) {
            parseSources("123:snes", listOf(source.copy(locator = source.locator.copy(path = "SNES/../other.zip"))))
        }
    }

    @Test fun `torrent file selection remains explicit and HTTP cannot smuggle another locator`() {
        val torrent = source.copy(kind = "torrent", locator = AddonLocator(infoHash = "a".repeat(40), fileIndex = 0, path = "Game.zip"))
        assertEquals(torrent, parseSources("123:snes", listOf(torrent)).entries["123:snes"]!!.single())
        assertFailure(AddonFailure.INVALID_SOURCE) { parseSources("123:snes", listOf(torrent.copy(locator = torrent.locator.copy(fileIndex = null)))) }
        assertFailure(AddonFailure.INVALID_SOURCE) { parseSources("123:snes", listOf(source.copy(kind = "http", locator = source.locator.copy(url = "https://sources.example/game.zip")))) }
    }

    @Test fun `source duplication and unbounded input are rejected`() {
        assertFailure(AddonFailure.INVALID_MANIFEST) { parseSources("123:snes", listOf(source, source)) }
        assertFailure(AddonFailure.INVALID_MANIFEST) {
            parseSources("123:snes", List(AddonFormat.MAX_SOURCES_PER_GAME + 1) { source.copy(id = "source-$it") })
        }
    }

    @Test fun `bounded stream accepts exact size and rejects one extra byte`() {
        assertEquals(32, ByteArrayInputStream(ByteArray(32)).readAddonBytes(32).size)
        assertFailure(AddonFailure.TOO_LARGE) { ByteArrayInputStream(ByteArray(33)).readAddonBytes(32) }
        assertFailure(AddonFailure.TOO_LARGE) { format.parseManifest(ByteArray(AddonFormat.MAX_MANIFEST_BYTES + 1)) }
    }

    @Test fun `manifest fingerprint changes when locators or version changes`() {
        assertNotEquals(format.fingerprint(manifest), format.fingerprint(manifest.copy(version = "2")))
        val installed = InstalledAddon(manifest, false)
        assertEquals(installed, format.parseInstalled(format.encodeInstalled(installed).toByteArray()))
    }

    private fun parseSources(key: String, sources: List<AddonSource>) =
        format.parseShard(shardBytes(AddonShard(1, mapOf(key to sources))), manifest, AddonFormat.partition(key))
    private fun manifestBytes(value: AddonManifest) = moshi.adapter(AddonManifest::class.java).toJson(value).toByteArray()
    private fun shardBytes(value: AddonShard) = moshi.adapter(AddonShard::class.java).toJson(value).toByteArray()
    private fun assertFailure(reason: AddonFailure, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (e: AddonException) { assertEquals(reason, e.reason) }
    }
}
