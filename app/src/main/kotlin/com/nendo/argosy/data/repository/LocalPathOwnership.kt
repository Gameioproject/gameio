package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.LocalFileOwner
import com.nendo.argosy.data.storage.FileAccessLayer
import java.io.File

/**
 * Canonical ownership prevents a platform alias or a disc filename from attaching another game.
 * A snapshot is held inside the catalog identity lock and extended as that scan claims files.
 */
internal class LocalPathOwnership(owners: List<LocalFileOwner>, private val access: FileAccessLayer) {
    private val paths = mutableMapOf<String, MutableSet<Long>>()
    private val directories = mutableMapOf<String, MutableSet<Long>>()

    init { owners.forEach { claim(it.localPath, it.gameId) } }

    fun claim(path: String, gameId: Long) {
        val identity = identity(path)
        paths.getOrPut(identity) { mutableSetOf() }.add(gameId)
        if (access.isDirectory(path)) directories.getOrPut(identity) { mutableSetOf() }.add(gameId)
    }

    fun isClaimed(path: String): Boolean = owners(path).isNotEmpty()

    fun hasForeignOwner(path: String, gameId: Long): Boolean = owners(path).any { it != gameId }

    private fun owners(path: String): Set<Long> {
        val identity = identity(path)
        return buildSet {
            addAll(paths[identity].orEmpty())
            directories.forEach { (directory, owners) ->
                if (identity.startsWith(directory + File.separator)) addAll(owners)
            }
        }
    }

    private fun identity(path: String): String =
        runCatching { access.getTransformedFile(path).canonicalPath }.getOrDefault(File(path).normalize().path)
}
