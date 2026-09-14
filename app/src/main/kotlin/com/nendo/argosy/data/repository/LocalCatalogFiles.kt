package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.util.FileNames
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.Normalizer

internal object LocalCatalogFiles {
    private val tags = Regex("\\s*(\\([^)]*\\)|\\[[^]]*])\\s*$")
    private val regionTag = Regex("(?i)(usa|europe|japan|world|asia|australia|korea|china|taiwan|brazil|canada|france|germany|italy|spain|uk|united kingdom)(,?\\s+(usa|europe|japan|world|asia|australia|korea|china|taiwan|brazil|canada|france|germany|italy|spain|uk|united kingdom))*")
    private val otherTag = Regex("(?i)(rev(?:ision)?\\s+[^ ]+|v\\d[\\d.]*|disc\\s+\\d+(?:\\s+of\\s+\\d+)?|disk\\s+\\d+|en(?:,\\w{2})*|[a-z]{2}(?:,[a-z]{2})+|!|[abfhopt]\\d*)")
    private val article = Regex("(?i)^(.*?), (the|a|an)\\b(.*)$")
    private val cueFile = Regex("(?i)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+")
    private val gdiFile = Regex("^\\s*\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+(?:\"([^\"]+)\"|(\\S+))\\s+-?\\d+")

    fun title(filename: String, extensions: Set<String>): String {
        var title = filename.substringBeforeLast('.')
        if (title.substringAfterLast('.', "").lowercase() in extensions) title = title.substringBeforeLast('.')
        while (true) {
            val match = tags.find(title) ?: break
            val content = match.groupValues[1].drop(1).dropLast(1).trim()
            if (!regionTag.matches(content) && !otherTag.matches(content)) break
            title = title.substring(0, match.range.first).trimEnd()
        }
        article.matchEntire(title)?.let { title = "${it.groupValues[2]} ${it.groupValues[1]}${it.groupValues[3]}" }
        return title.replace('_', ' ').trim().ifBlank { filename.substringBeforeLast('.') }
    }

    fun key(title: String): String = FileNames.normalizeForMatch(
        Normalizer.normalize(title, Normalizer.Form.NFC)
    ).replace(Regex("[\\p{Punct}\\s]+"), "")

    fun provisionalMatch(games: List<GameEntity>, title: String, igdbId: Long?): GameEntity? {
        if (igdbId == null) return null
        return games.filter { game ->
            game.source == GameSource.LOCAL_ONLY && game.rommId == null && game.localPath != null &&
                (game.igdbId == igdbId || (game.igdbId == null && key(game.title) == key(title)))
        }.singleOrNull()
    }

    fun candidates(files: List<FileInfo>, extensions: Set<String>, access: FileAccessLayer): List<FileInfo> {
        val supported = files.filter { it.isFile && it.extension.lowercase() in extensions && !it.name.startsWith('.') }
        val referenced = supported.flatMap { file ->
            if (file.extension.lowercase() !in setOf("m3u", "m3u8", "cue", "gdi") || file.size > 256 * 1024) return@flatMap emptyList()
            val bytes = readDescriptor(file.path, access) ?: return@flatMap emptyList()
            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            referenceNames(file.extension.lowercase(), text).map { name ->
                File(file.parent, name).normalize().path
            }
        }.toSet()
        return supported.filter { File(it.path).normalize().path !in referenced }
    }

    internal fun referenceNames(extension: String, text: String): List<String> = text.lineSequence().mapNotNull { line ->
        when (extension) {
            "m3u", "m3u8" -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith('#') }
            "cue" -> cueFile.find(line)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            "gdi" -> gdiFile.find(line)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            else -> null
        }
    }.toList()

    private fun readDescriptor(path: String, access: FileAccessLayer): ByteArray? = try {
        access.getInputStream(path)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 256 * 1024) return@use null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
