package com.nendo.argosy.data.addon

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonShardCache @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, "addon-shards")

    @Synchronized
    fun read(fingerprint: String, shard: String, maxAgeMs: Long): ByteArray? {
        val file = File(directory, "$fingerprint-$shard.json")
        val age = System.currentTimeMillis() - file.lastModified()
        if (!file.exists() || age < 0 || age > maxAgeMs || file.length() > AddonFormat.MAX_SHARD_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    @Synchronized
    fun write(fingerprint: String, shard: String, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) return
        runCatching {
            val files = directory.listFiles().orEmpty().sortedBy { it.lastModified() }
            var size = files.sumOf { it.length() } + bytes.size
            for (file in files) {
                if (size <= MAX_CACHE_BYTES) break
                val length = file.length()
                if (file.delete()) size -= length
            }
            val atomic = AtomicFile(File(directory, "$fingerprint-$shard.json"))
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (e: Exception) {
                atomic.failWrite(output)
                throw e
            }
        }
    }

    @Synchronized
    fun invalidate(fingerprint: String) {
        directory.listFiles().orEmpty().filter { it.name.startsWith("$fingerprint-") }.forEach { it.delete() }
    }

    companion object {
        private const val MAX_CACHE_BYTES = 32 * 1024 * 1024L
    }
}
