package com.nendo.argosy.data.addon

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonStore @Inject constructor(
    @ApplicationContext context: Context,
    private val format: AddonFormat
) {
    private val directory = File(context.filesDir, "addons")
    private val mutex = Mutex()
    private var loaded = false
    private val _addons = MutableStateFlow<List<InstalledAddon>>(emptyList())
    val addons = _addons.asStateFlow()
    private val _unreadableCount = MutableStateFlow(0)
    val unreadableCount = _unreadableCount.asStateFlow()
    private var unreadableFiles = emptySet<String>()

    suspend fun load(): List<InstalledAddon> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                val unreadable = mutableSetOf<String>()
                val savedFiles = if (directory.exists()) {
                    directory.listFiles() ?: throw AddonException(AddonFailure.STORAGE)
                } else emptyArray()
                val records = savedFiles
                    .filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
                    .map { it.name.removeSuffix(".bak") }.distinct().mapNotNull { name ->
                        try {
                            val bytes = AtomicFile(File(directory, name)).openRead().use {
                                it.readAddonBytes(AddonFormat.MAX_MANIFEST_BYTES + 1024)
                            }
                            format.parseInstalled(bytes)
                        } catch (_: Exception) {
                            unreadable.add(name)
                            null
                        }
                    }
                unreadableFiles = unreadable
                _unreadableCount.value = unreadable.size
                _addons.value = records.sortedBy { it.manifest.name.lowercase() }
                loaded = true
            }
            _addons.value
        }
    }

    suspend fun import(bytes: ByteArray): InstalledAddon {
        val manifest = withContext(Dispatchers.Default) { format.parseManifest(bytes) }
        load()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val existing = _addons.value.find { it.manifest.id == manifest.id }
                if (existing == null && _addons.value.size >= MAX_ADDONS) throw AddonException(AddonFailure.TOO_LARGE)
                val record = InstalledAddon(manifest, existing?.enabled ?: true)
                write(record)
                unreadableFiles = unreadableFiles - recordFile(manifest.id).name
                _unreadableCount.value = unreadableFiles.size
                _addons.update { records ->
                    (records.filterNot { it.manifest.id == manifest.id } + record)
                        .sortedBy { it.manifest.name.lowercase() }
                }
                record
            }
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        load()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val record = _addons.value.find { it.manifest.id == id }
                    ?: throw AddonException(AddonFailure.REMOVED)
                val updated = record.copy(enabled = enabled)
                write(updated)
                _addons.update { records -> records.map { if (it.manifest.id == id) updated else it } }
            }
        }
    }

    suspend fun remove(id: String) {
        load()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val record = _addons.value.find { it.manifest.id == id } ?: return@withLock
                val file = recordFile(record.manifest.id)
                AtomicFile(file).delete()
                if (file.exists() || File("${file.path}.bak").exists()) throw AddonException(AddonFailure.STORAGE)
                _addons.update { records -> records.filterNot { it.manifest.id == id } }
            }
        }
    }

    private fun recordFile(id: String) = File(directory, "${AddonFormat.sha256(id)}.json")

    private fun write(record: InstalledAddon) {
        if (!directory.isDirectory && !directory.mkdirs()) throw AddonException(AddonFailure.STORAGE)
        val file = AtomicFile(recordFile(record.manifest.id))
        val output = try { file.startWrite() } catch (e: IOException) {
            throw AddonException(AddonFailure.STORAGE, e)
        }
        try {
            output.write(format.encodeInstalled(record).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw AddonException(AddonFailure.STORAGE, e)
        }
    }

    companion object {
        const val MAX_ADDONS = 20
    }
}
