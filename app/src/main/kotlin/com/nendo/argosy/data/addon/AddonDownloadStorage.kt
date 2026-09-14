package com.nendo.argosy.data.addon

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A source snapshot owns its transfer and extracted files independently of provider filenames.
 * Hidden work stays out of folder discovery; completed games use their recorded launch path.
 */
@Singleton
class AddonDownloadStorage @Inject constructor() {
    @Synchronized
    fun directory(platformDir: File, gameId: Long, sourceJson: String, create: Boolean = true): File {
        try {
            if (gameId <= 0 || sourceJson.isBlank()) throw AddonException(AddonFailure.INVALID_SOURCE)
            val hash = MessageDigest.getInstance("SHA-256").digest(sourceJson.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val root = File(platformDir.canonicalFile, ".gameio-addons/$gameId/$hash")
            if (root.canonicalFile != root.absoluteFile) throw AddonException(AddonFailure.STORAGE)
            val marker = File(root, ".owner")
            val identity = "1\n$gameId\n$hash\n"
            if (!root.exists() && create) {
                val parent = root.parentFile ?: throw AddonException(AddonFailure.STORAGE)
                if (!parent.isDirectory && !parent.mkdirs()) throw AddonException(AddonFailure.STORAGE)
                if (!root.mkdir()) throw AddonException(AddonFailure.STORAGE)
                marker.writeText(identity)
            }
            if (root.exists() && (!root.isDirectory || !marker.isFile ||
                    marker.canonicalFile != marker.absoluteFile || marker.length() != identity.length.toLong() ||
                    marker.readText() != identity)) {
                throw AddonException(AddonFailure.STORAGE)
            }
            val content = File(root, "content")
            if (content.canonicalFile != content.absoluteFile) throw AddonException(AddonFailure.STORAGE)
            if (create && !content.isDirectory && !content.mkdir()) throw AddonException(AddonFailure.STORAGE)
            return content
        } catch (e: IOException) {
            throw AddonException(AddonFailure.STORAGE, e)
        } catch (e: SecurityException) {
            throw AddonException(AddonFailure.STORAGE, e)
        }
    }

    fun requireDestination(expected: File, actual: File) {
        if (actual.absoluteFile.normalize() != expected.absoluteFile.normalize() ||
            actual.canonicalFile != expected.canonicalFile) throw AddonException(AddonFailure.STORAGE)
    }

    fun requireContained(directory: File, file: File) {
        if (!file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
            throw AddonException(AddonFailure.STORAGE)
        }
    }
}
