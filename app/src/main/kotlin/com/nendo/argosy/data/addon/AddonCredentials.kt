package com.nendo.argosy.data.addon

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonCredentials internal constructor(context: Context, private val keyAlias: String) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, KEY_ALIAS)
    private val file = AtomicFile(File(context.noBackupFilesDir, "addon-realdebrid.bin"))
    private val mutex = Mutex()
    private var loaded = false
    private var record: Record? = null
    private val _hasAccount = MutableStateFlow(false)
    val hasAccount = _hasAccount.asStateFlow()

    internal suspend fun token(): String? = access { record?.token }

    internal suspend fun save(token: String) = access {
        write(Record(token, emptyMap()))
    }

    internal suspend fun torrentId(key: String): String? = access { record?.torrents?.get(key) }

    internal suspend fun rememberTorrent(key: String, id: String?) = access {
        val current = record ?: throw AddonException(AddonFailure.ACCOUNT_REQUIRED)
        val entries = (current.torrents - key).toMutableMap().apply {
            if (id != null) put(key, id)
        }.entries.toList().takeLast(MAX_TORRENTS).associate { it.key to it.value }
        write(Record(current.token, entries))
    }

    internal suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.delete()
            if (file.baseFile.exists() || File("${file.baseFile.path}.bak").exists()) {
                throw AddonException(AddonFailure.STORAGE)
            }
            record = null
            loaded = true
            _hasAccount.update { false }
            try {
                keyStore().deleteEntry(keyAlias)
            } catch (e: Exception) {
                throw AddonException(AddonFailure.STORAGE, e)
            }
        }
    }

    private suspend fun <T> access(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!loaded) {
                    record = read()
                    loaded = true
                    _hasAccount.update { record != null }
                }
                block()
            } catch (e: AddonException) {
                throw e
            } catch (e: Exception) {
                throw AddonException(AddonFailure.STORAGE, e)
            }
        }
    }

    private fun read(): Record? {
        if (!file.baseFile.exists() && !File("${file.baseFile.path}.bak").exists()) return null
        val bytes = file.openRead().use { it.readAddonBytes(MAX_CREDENTIAL_BYTES) }
        if (bytes.size < IV_BYTES + 17 || bytes[0] != 1.toByte()) throw AddonException(AddonFailure.STORAGE)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(1, IV_BYTES + 1)))
        val plaintext = cipher.doFinal(bytes, IV_BYTES + 1, bytes.size - IV_BYTES - 1)
        try {
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            val token = json.getString("token")
            if (!validToken(token)) throw AddonException(AddonFailure.STORAGE)
            val torrents = json.optJSONObject("torrents") ?: JSONObject()
            if (torrents.length() > MAX_TORRENTS) throw AddonException(AddonFailure.STORAGE)
            return Record(token, torrents.keys().asSequence().associateWith { torrents.getString(it) })
        } finally {
            plaintext.fill(0)
        }
    }

    private fun write(value: Record) {
        val plaintext = JSONObject().put("token", value.token)
            .put("torrents", JSONObject(value.torrents)).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val output = file.startWrite()
        try {
            output.write(byteArrayOf(1) + cipher.iv + encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
        record = value
        _hasAccount.update { true }
    }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private class Record(val token: String, val torrents: Map<String, String>)

    companion object {
        private const val KEY_ALIAS = "gameio.addon.realdebrid.v1"
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val MAX_TORRENTS = 128
        private const val MAX_CREDENTIAL_BYTES = 32 * 1024

        internal fun validToken(value: String): Boolean = value.length in 1..2048 &&
            value.all { it.code in 33..126 }
    }
}
