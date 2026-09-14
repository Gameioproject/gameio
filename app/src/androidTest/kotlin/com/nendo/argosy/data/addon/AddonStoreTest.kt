package com.nendo.argosy.data.addon

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AddonStoreTest {
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    private val format = AddonFormat(Moshi.Builder().build())
    private val manifest = AddonManifest(1, "test", "Test add-on", "1", "catalog-shards-v1",
        AddonLookup("igdbId:platformSlug", "sha256-prefix-2", "https://example.org/{shard}.json"), listOf("example.org"))
    private fun bytes(value: AddonManifest = manifest) = Moshi.Builder().build()
        .adapter(AddonManifest::class.java).toJson(value).toByteArray()

    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "addon-store-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) { override fun getFilesDir(): File = directory }
    }

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun importedDisabledAddonSurvivesProcessRepositoryReplacement() = runBlocking {
        val first = AddonStore(context, format)
        first.import(bytes())
        first.setEnabled("test", false)
        val second = AddonStore(context, format)
        assertEquals(listOf(InstalledAddon(manifest, false)), second.load())
        second.import(bytes(manifest.copy(version = "2")))
        assertFalse(second.addons.value.single().enabled)
        assertEquals("2", AddonStore(context, format).load().single().manifest.version)
    }

    @Test fun corruptRecordIsPreservedAndReimportRepairsOnlyThatAddon() = runBlocking {
        AddonStore(context, format).import(bytes())
        val record = File(directory, "addons/${AddonFormat.sha256("test")}.json")
        record.writeText("unfinished record")
        val restarted = AddonStore(context, format)
        assertTrue(restarted.load().isEmpty())
        assertEquals(1, restarted.unreadableCount.value)
        assertEquals("unfinished record", record.readText())
        restarted.import(bytes(manifest.copy(id = "other", name = "Other")))
        restarted.import(bytes())
        assertEquals(0, restarted.unreadableCount.value)
        assertEquals(setOf("test", "other"), AddonStore(context, format).load().map { it.manifest.id }.toSet())
        restarted.remove("test")
        assertEquals("other", AddonStore(context, format).load().single().manifest.id)
    }

    @Test fun unreadableStoreIsReportedInsteadOfAppearingEmpty() = runBlocking {
        File(directory, "addons").writeText("not a directory")
        try { AddonStore(context, format).load(); fail("Expected storage failure") }
        catch (e: AddonException) { assertEquals(AddonFailure.STORAGE, e.reason) }
        assertEquals("not a directory", File(directory, "addons").readText())
    }
}
