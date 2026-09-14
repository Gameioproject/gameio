package com.nendo.argosy.data.addon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonFileVerifier @Inject constructor(private val format: AddonFormat) {
    suspend fun verify(file: File, sourceJson: String) = withContext(Dispatchers.IO) {
        val source = format.parseMatch(sourceJson).source
        if (source.size != null && file.length() != source.size) throw AddonException(AddonFailure.INTEGRITY)
        val md5 = source.md5?.let { MessageDigest.getInstance("MD5") }
        val sha1 = source.sha1?.let { MessageDigest.getInstance("SHA-1") }
        if (md5 == null && sha1 == null) return@withContext
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                md5?.update(buffer, 0, count)
                sha1?.update(buffer, 0, count)
            }
        }
        fun matches(digest: MessageDigest?, expected: String?) =
            digest == null || digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
        if (!matches(md5, source.md5) || !matches(sha1, source.sha1)) throw AddonException(AddonFailure.INTEGRITY)
    }
}
