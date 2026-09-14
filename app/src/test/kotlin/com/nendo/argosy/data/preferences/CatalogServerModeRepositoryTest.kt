package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogServerModeRepositoryTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun `successful catalog mode survives process restart and trailing slash normalization`() = runTest {
        val store = MemoryStore()
        val first = CatalogServerModeRepository(store)
        first.select("https://CATALOG.example/")
        first.remember("https://catalog.example", true)
        val restarted = CatalogServerModeRepository(store)
        restarted.select("https://catalog.example/")
        assertTrue(restarted.usesAddonSources())
        restarted.select("https://catalog.example")
        assertTrue(restarted.usesAddonSources())
    }

    @Test fun `another server and signed out session never inherit catalog mode`() = runTest {
        val repository = CatalogServerModeRepository(MemoryStore())
        repository.select("https://catalog.example")
        repository.remember("https://catalog.example", true)
        repository.select("https://files.example")
        assertFalse(repository.usesAddonSources())
        repository.select("https://catalog.example")
        assertTrue(repository.usesAddonSources())
        repository.clearSelection()
        assertFalse(repository.usesAddonSources())
        repository.select("https://catalog.example")
        assertTrue(repository.usesAddonSources())
    }

    @Test fun `fresh server mode replaces earlier heartbeat without enabling other capabilities`() = runTest {
        val repository = CatalogServerModeRepository(MemoryStore())
        repository.select("https://catalog.example")
        repository.remember("https://catalog.example", true)
        repository.remember("https://catalog.example", false)
        repository.clearSelection()
        repository.select("https://catalog.example")
        assertFalse(repository.usesAddonSources())
    }

    @Test fun `late heartbeat from previous server does not change active selection`() = runTest {
        val repository = CatalogServerModeRepository(MemoryStore())
        repository.select("https://catalog.example")
        repository.select("https://files.example")
        repository.remember("https://catalog.example", true)
        assertFalse(repository.usesAddonSources())
    }
}
