package com.nendo.argosy.data.addon

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AddonExportCorpusTest {
    @Test fun `client accepts all shards from a controlled server export`() {
        val path = System.getenv("GAMEIO_ADDON_TEST_DIR")
        assumeTrue("Set GAMEIO_ADDON_TEST_DIR to a private source export", path != null)
        val directory = File(path!!)
        val format = AddonFormat(Moshi.Builder().build())
        val manifest = format.parseManifest(File(directory, "manifest.json").readBytes())
        val shards = directory.walkTopDown().filter { it.isFile && it.name.matches(Regex("[0-9a-f]{2}\\.json")) }.toList()
        assertEquals(256, shards.size)
        var totalSources = 0
        shards.forEach { file ->
            val shard = format.parseShard(file.readBytes(), manifest, file.nameWithoutExtension)
            totalSources += shard.entries.values.sumOf { it.size }
        }
        assertTrue("Expected the actual populated export", totalSources > 0)
        println("Validated ${shards.size} shards and $totalSources source mappings")
    }
}
