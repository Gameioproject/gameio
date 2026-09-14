package com.nendo.argosy.data.addon

import com.nendo.argosy.data.storage.FileAccessLayer
import com.squareup.moshi.Moshi
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AddonRepositoryTest {
    private val manifest = AddonManifest(1, "test", "Test", "1", "catalog-shards-v1",
        AddonLookup("igdbId:platformSlug", "sha256-prefix-2", "https://example.org/{shard}.json"), listOf("example.org"))
    private val source = AddonSource("one", "http", "Game.zip", AddonLocator(url = "https://example.org/Game.zip"))
    private val store = mockk<AddonStore>()
    private val cache = mockk<AddonShardCache>(relaxed = true)
    private val http = mockk<AddonHttpClient>()
    private val files = mockk<FileAccessLayer>()
    private val moshi = Moshi.Builder().build()
    private val format = AddonFormat(moshi)
    private lateinit var repository: AddonRepository

    @Before fun setup() {
        every { store.addons } returns MutableStateFlow(listOf(InstalledAddon(manifest)))
        every { store.unreadableCount } returns MutableStateFlow(0)
        coEvery { store.load() } returns listOf(InstalledAddon(manifest))
        every { cache.read(any(), any(), any()) } returns null
        repository = AddonRepository(store, format, http, cache, files)
    }

    @Test fun `no add-on never initiates a provider request`() = runBlocking {
        coEvery { store.load() } returns emptyList()
        val result = repository.lookup(123, "snes")
        assertEquals(0, result.enabledAddonCount)
        assertFalse(result.isConfirmedMissing)
        coVerify(exactly = 0) { http.bytes(any(), any(), any()) }
    }

    @Test fun `one game lookup fetches only its own bounded shard`() = runBlocking {
        coEvery { http.bytes(any(), any(), any()) } returns shard(listOf(source))
        val result = repository.lookup(123, "snes")
        assertEquals("123:snes", result.sources.single().catalogKey)
        coVerify(exactly = 1) {
            http.bytes(match { it.encodedPath == "/${AddonFormat.partition("123:snes")}.json" }, AddonFormat.MAX_SHARD_BYTES, any())
        }
    }

    @Test fun `confirmed miss is different from provider outage or missing shard`() = runBlocking {
        coEvery { http.bytes(any(), any(), any()) } returns shard(emptyList())
        assertTrue(repository.lookup(123, "snes").isConfirmedMissing)
        coEvery { http.bytes(any(), any(), any()) } throws AddonException(AddonFailure.NOT_FOUND)
        val outage = repository.lookup(123, "snes")
        assertFalse(outage.isConfirmedMissing)
        assertEquals(AddonFailure.NOT_FOUND, outage.failures.single().reason)
    }

    @Test fun `concurrent identical lookups share cached request`() = runBlocking {
        var cached: ByteArray? = null
        every { cache.read(any(), any(), any()) } answers { cached }
        every { cache.write(any(), any(), any()) } answers { cached = thirdArg() }
        coEvery { http.bytes(any(), any(), any()) } coAnswers { delay(50); shard(listOf(source)) }
        val first = async { repository.lookup(123, "snes") }
        val second = async { repository.lookup(123, "snes") }
        assertEquals(first.await(), second.await())
        coVerify(exactly = 1) { http.bytes(any(), any(), any()) }
    }

    @Test fun `cancellation is not shown or cached as a missing game`() = runBlocking {
        coEvery { http.bytes(any(), any(), any()) } throws CancellationException("left screen")
        try { repository.lookup(123, "snes"); fail("Expected cancellation") } catch (_: CancellationException) { }
        verify(exactly = 0) { cache.write(any(), any(), any()) }
    }

    @Test fun `removal during lookup cannot return stale sources`() = runBlocking {
        coEvery { http.bytes(any(), any(), any()) } coAnswers {
            coEvery { store.load() } returns emptyList()
            shard(listOf(source))
        }
        assertTrue(repository.lookup(123, "snes").sources.isEmpty())
    }

    @Test fun `failed reimport leaves existing provider unchanged`() = runBlocking {
        val bytes = moshi.adapter(AddonManifest::class.java).toJson(manifest.copy(id = "different")).toByteArray()
        try { repository.import(bytes, "test"); fail("Expected mismatch") }
        catch (e: AddonException) { assertEquals(AddonFailure.INVALID_MANIFEST, e.reason) }
        coVerify(exactly = 0) { store.import(any()) }
        verify(exactly = 0) { cache.invalidate(any()) }
    }

    @Test fun `disabled provider is never contacted`() = runBlocking {
        coEvery { store.load() } returns listOf(InstalledAddon(manifest, false))
        assertEquals(0, repository.lookup(123, "snes").enabledAddonCount)
        coVerify(exactly = 0) { http.bytes(any(), any(), any()) }
    }

    private fun shard(sources: List<AddonSource>): ByteArray = moshi.adapter(AddonShard::class.java)
        .toJson(AddonShard(1, mapOf("123:snes" to sources))).toByteArray()
}
