package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Remembers only where game content comes from. Cached mode never enables any sync capability.
 */
@Singleton
class CatalogServerModeRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private data class Selection(val server: String?, val catalogOnly: Boolean = false)
    private val selection = AtomicReference(Selection(null))

    fun usesAddonSources(): Boolean = selection.get().catalogOnly

    fun clearSelection() { selection.set(Selection(null)) }

    suspend fun select(serverUrl: String) {
        val server = normalize(serverUrl)
        if (selection.get().server == server) return
        val pending = Selection(server)
        selection.set(pending)
        if (server == null) return
        val remembered = dataStore.data.first()[key(server)] ?: false
        selection.compareAndSet(pending, pending.copy(catalogOnly = remembered))
    }

    suspend fun remember(serverUrl: String, catalogOnly: Boolean) {
        val server = normalize(serverUrl) ?: return
        selection.updateAndGet { if (it.server == server) it.copy(catalogOnly = catalogOnly) else it }
        dataStore.edit { it[key(server)] = catalogOnly }
    }

    private fun normalize(raw: String): String? = raw.trim().toHttpUrlOrNull()?.newBuilder()
        ?.username("")?.password("")?.query(null)?.fragment(null)?.build()?.toString()?.trimEnd('/')

    private fun key(server: String): Preferences.Key<Boolean> {
        val digest = MessageDigest.getInstance("SHA-256").digest(server.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return booleanPreferencesKey("catalog_only_server_$digest")
    }
}
