package com.nendo.argosy.data.addon

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonFormat @Inject constructor(moshi: Moshi) {
    private val manifestAdapter = moshi.adapter(AddonManifest::class.java)
    private val shardAdapter = moshi.adapter(AddonShard::class.java)
    private val installedAdapter = moshi.adapter(InstalledAddon::class.java)
    private val matchAdapter = moshi.adapter(AddonSourceMatch::class.java)

    fun parseManifest(bytes: ByteArray): AddonManifest = parse(bytes, MAX_MANIFEST_BYTES) {
        manifestAdapter.fromJson(it)?.also(::validateManifest)
    }

    fun parseShard(bytes: ByteArray, manifest: AddonManifest, shard: String): AddonShard =
        parse(bytes, MAX_SHARD_BYTES) { text ->
            shardAdapter.fromJson(text)?.also { parsed ->
                requireValid(parsed.schemaVersion == 1, AddonFailure.UNSUPPORTED_VERSION)
                requireValid(parsed.entries.size <= MAX_SHARD_KEYS)
                parsed.entries.forEach { (key, sources) ->
                    requireValid(KEY_PATTERN.matches(key) && partition(key) == shard)
                    requireValid(sources.size <= MAX_SOURCES_PER_GAME)
                    requireValid(sources.map { it.id }.distinct().size == sources.size)
                    sources.forEach { validateSource(it, manifest) }
                }
            }
        }

    fun encodeInstalled(addon: InstalledAddon): String = installedAdapter.toJson(addon)

    fun encodeMatch(match: AddonSourceMatch): String = matchAdapter.toJson(match)

    fun parseMatch(json: String): AddonSourceMatch = parse(json.toByteArray(Charsets.UTF_8), MAX_MANIFEST_BYTES) {
        matchAdapter.fromJson(it)?.also { match ->
            requireValid(KEY_PATTERN.matches(match.catalogKey) && ID_PATTERN.matches(match.addonId) &&
                Regex("[a-f0-9]{64}").matches(match.manifestFingerprint), AddonFailure.INVALID_SOURCE)
        }
    }

    fun validateMatch(match: AddonSourceMatch, manifest: AddonManifest) {
        requireValid(match.addonId == manifest.id && match.manifestFingerprint == fingerprint(manifest),
            AddonFailure.REMOVED)
        validateSource(match.source, manifest)
    }

    fun parseInstalled(bytes: ByteArray): InstalledAddon = parse(bytes, MAX_MANIFEST_BYTES + 1024) {
        installedAdapter.fromJson(it)?.also { addon -> validateManifest(addon.manifest) }
    }

    fun fingerprint(manifest: AddonManifest): String = sha256(manifestAdapter.toJson(manifest))

    fun shardUrl(manifest: AddonManifest, key: String): HttpUrl =
        approvedUrl(manifest.lookup.urlTemplate.replace("{shard}", partition(key)), manifest.allowedHosts)

    fun approvedUrl(value: String, allowedHosts: List<String>): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw AddonException(AddonFailure.UNTRUSTED_HOST)
        requireValid(url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            url.fragment == null && url.host in allowedHosts, AddonFailure.UNTRUSTED_HOST)
        return url
    }

    private fun validateManifest(value: AddonManifest) {
        requireValid(value.schemaVersion == 1 && value.adapter == "catalog-shards-v1" &&
            value.lookup.key == "igdbId:platformSlug" && value.lookup.partition == "sha256-prefix-2",
            AddonFailure.UNSUPPORTED_VERSION)
        requireValid(ID_PATTERN.matches(value.id))
        requireValid(value.name.isNotBlank() && value.name.length <= 100 && value.name.none(Char::isISOControl))
        requireValid(value.version.isNotBlank() && value.version.length <= 100)
        requireValid(value.allowedHosts.isNotEmpty() && value.allowedHosts.size <= 64 &&
            value.allowedHosts.distinct().size == value.allowedHosts.size)
        value.allowedHosts.forEach { host ->
            val url = "https://$host/".toHttpUrlOrNull()
            requireValid(host.length <= 253 && url != null && url.host == host &&
                url.encodedPath == "/" && url.query == null && url.port == 443 &&
                url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
        }
        requireValid(value.lookup.urlTemplate.length <= 2048 &&
            value.lookup.urlTemplate.windowed(7).count { it == "{shard}" } == 1)
        val replaced = value.lookup.urlTemplate.replace("{shard}", "00")
        requireValid('{' !in replaced && '}' !in replaced)
        approvedUrl(replaced, value.allowedHosts)
    }

    private fun validateSource(source: AddonSource, manifest: AddonManifest) {
        requireValid(ID_PATTERN.matches(source.id), AddonFailure.INVALID_SOURCE)
        requireValid(source.filename.isNotBlank() && source.filename.length <= 500 &&
            source.filename !in setOf(".", "..") &&
            source.filename.none { it == '/' || it == '\\' || it.isISOControl() }, AddonFailure.INVALID_SOURCE)
        requireValid(source.size == null || source.size > 0, AddonFailure.INVALID_SOURCE)
        requireValid(source.md5 == null || HEX_32.matches(source.md5), AddonFailure.INVALID_SOURCE)
        requireValid(source.sha1 == null || HEX_40.matches(source.sha1), AddonFailure.INVALID_SOURCE)
        requireValid(source.region == null || source.region.length <= 100, AddonFailure.INVALID_SOURCE)
        val loc = source.locator
        when (source.kind) {
            "internet_archive" -> {
                requireValid(loc.item != null && IA_ITEM.matches(loc.item) && validPath(loc.path), AddonFailure.INVALID_SOURCE)
                requireValid(loc.url == null && loc.infoHash == null && loc.fileIndex == null, AddonFailure.INVALID_SOURCE)
            }
            "http" -> {
                approvedUrl(loc.url ?: throw AddonException(AddonFailure.INVALID_SOURCE), manifest.allowedHosts)
                requireValid(loc.item == null && loc.path == null && loc.infoHash == null && loc.fileIndex == null,
                    AddonFailure.INVALID_SOURCE)
            }
            "torrent" -> {
                requireValid(loc.infoHash != null && HEX_40.matches(loc.infoHash) &&
                    loc.fileIndex != null && loc.fileIndex >= 0 && validPath(loc.path), AddonFailure.INVALID_SOURCE)
                requireValid(loc.item == null && loc.url == null, AddonFailure.INVALID_SOURCE)
            }
            else -> throw AddonException(AddonFailure.INVALID_SOURCE)
        }
    }

    private fun <T> parse(bytes: ByteArray, maxSize: Int, parse: (String) -> T?): T {
        requireValid(bytes.size <= maxSize, AddonFailure.TOO_LARGE)
        return try {
            parse(bytes.toString(Charsets.UTF_8)) ?: throw AddonException(AddonFailure.INVALID_MANIFEST)
        } catch (e: AddonException) {
            throw e
        } catch (e: Exception) {
            throw AddonException(AddonFailure.INVALID_MANIFEST, e)
        }
    }

    companion object {
        const val MAX_MANIFEST_BYTES = 64 * 1024
        const val MAX_SHARD_BYTES = 1024 * 1024
        const val MAX_SHARD_KEYS = 1000
        const val MAX_SOURCES_PER_GAME = 200
        private val ID_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")
        private val KEY_PATTERN = Regex("[1-9][0-9]*:[a-z0-9][a-z0-9-]{0,99}")
        private val IA_ITEM = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,199}")
        private val HEX_32 = Regex("[a-fA-F0-9]{32}")
        private val HEX_40 = Regex("[a-fA-F0-9]{40}")

        fun key(igdbId: Long, platformSlug: String): String = "$igdbId:$platformSlug".also {
            requireValid(KEY_PATTERN.matches(it), AddonFailure.INVALID_SOURCE)
        }

        fun partition(key: String): String = sha256(key).take(2)

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private fun validPath(path: String?): Boolean = path != null && path.isNotBlank() &&
            path.length <= 2000 && !path.startsWith('/') &&
            path.none { it == '\\' || it.isISOControl() } && path.split('/').none { it == ".." || it == "." || it.isEmpty() }

        private fun requireValid(valid: Boolean, reason: AddonFailure = AddonFailure.INVALID_MANIFEST) {
            if (!valid) throw AddonException(reason)
        }
    }
}
