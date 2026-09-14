package com.nendo.argosy.data.addon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonDebridResolver internal constructor(
    private val credentials: AddonCredentials,
    private val client: OkHttpClient,
    private val nowMillis: () -> Long
) {
    @Inject constructor(credentials: AddonCredentials) : this(credentials,
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).followRedirects(false)
            .followSslRedirects(false).build(), System::currentTimeMillis)

    private val mutex = Mutex()
    private var retryAfterMillis = 0L
    val hasAccount = credentials.hasAccount

    suspend fun loadAccount(): Boolean = credentials.token() != null

    suspend fun setToken(token: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val trimmed = token.trim()
            if (!AddonCredentials.validToken(trimmed)) throw AddonException(AddonFailure.ACCOUNT_REJECTED)
            val user = request("user", trimmed)
            if (user?.optString("type") != "premium") throw AddonException(AddonFailure.ACCOUNT_REJECTED)
            credentials.save(trimmed)
        }
    }

    suspend fun disconnect() = mutex.withLock { credentials.clear() }

    suspend fun resolve(source: AddonSource): HttpUrl = withContext(Dispatchers.IO) {
        mutex.withLock {
            val token = credentials.token() ?: throw AddonException(AddonFailure.ACCOUNT_REQUIRED)
            val locator = source.locator
            val hash = locator.infoHash?.lowercase()
            val path = locator.path
            if (source.kind != "torrent" || hash == null || !HASH.matches(hash) ||
                path.isNullOrBlank() || locator.fileIndex == null || locator.fileIndex < 0) {
                throw AddonException(AddonFailure.INVALID_SOURCE)
            }
            val key = AddonFormat.sha256("$hash:${normalizedPath(path)}")
            var torrentId = credentials.torrentId(key)
            var info = torrentId?.let { request("torrents/info/$it", token, allowMissing = true) }
            if (info == null) {
                val magnet = "magnet:?xt=urn:btih:$hash&so=${locator.fileIndex}"
                torrentId = request("torrents/addMagnet", token, mapOf("magnet" to magnet))
                    ?.optString("id")?.takeIf { TORRENT_ID.matches(it) }
                    ?: throw AddonException(AddonFailure.INVALID_SOURCE)
                credentials.rememberTorrent(key, torrentId)
            }
            val id = torrentId
            repeat(POLL_ATTEMPTS) { attempt ->
                val current = info ?: request("torrents/info/$id", token)
                    ?: throw AddonException(AddonFailure.NETWORK)
                if (!current.optString("hash").equals(hash, ignoreCase = true)) {
                    credentials.rememberTorrent(key, null)
                    throw AddonException(AddonFailure.INVALID_SOURCE)
                }
                val status = current.optString("status")
                if (status in FAILED_STATUSES) {
                    credentials.rememberTorrent(key, null)
                    throw AddonException(AddonFailure.INVALID_SOURCE)
                }
                if (status != "magnet_conversion") {
                    val file = selectedFile(current, path)
                    if (status == "waiting_files_selection") {
                        request("torrents/selectFiles/$id", token, mapOf("files" to file.getInt("id").toString()))
                    } else if (status == "downloaded") {
                        val link = singleSelectedLink(current, file.getInt("id"))
                        val unrestricted = request("unrestrict/link", token, mapOf("link" to link))
                            ?: throw AddonException(AddonFailure.NETWORK)
                        return@withLock downloadUrl(unrestricted.optString("download"))
                    }
                }
                if (attempt < POLL_ATTEMPTS - 1) delay(POLL_INTERVAL_MILLIS)
                info = null
            }
            throw AddonException(AddonFailure.SOURCE_NOT_READY)
        }
    }

    private suspend fun request(
        path: String,
        token: String,
        form: Map<String, String>? = null,
        allowMissing: Boolean = false
    ): JSONObject? {
        if (nowMillis() < retryAfterMillis) throw AddonException(AddonFailure.SOURCE_NOT_READY)
        val request = Request.Builder().url(API.newBuilder().addPathSegments(path).build())
            .header("Authorization", "Bearer $token").apply {
                form?.let { values -> post(FormBody.Builder().apply {
                    values.forEach { (name, value) -> add(name, value) }
                }.build()) }
            }.build()
        try {
            return client.newCall(request).await().use { response ->
                if (allowMissing && response.code == 404) return@use null
                if (response.code == 401) throw AddonException(AddonFailure.ACCOUNT_REJECTED)
                if (response.code == 429) {
                    retryAfterMillis = cooldown(response.header("Retry-After"), nowMillis())
                    throw AddonException(AddonFailure.SOURCE_NOT_READY)
                }
                if (response.code in setOf(202, 204)) return@use JSONObject()
                val body = response.body ?: throw AddonException(AddonFailure.NETWORK)
                val bytes = body.byteStream().readAddonBytes(MAX_RESPONSE_BYTES)
                val json = try { JSONObject(bytes.toString(Charsets.UTF_8)) }
                    catch (_: Exception) { throw AddonException(AddonFailure.NETWORK) }
                if (!response.isSuccessful || json.has("error")) {
                    val reason = when (json.optInt("error_code", -1)) {
                        8, 9, 14, 15, 20 -> AddonFailure.ACCOUNT_REJECTED
                        5, 18, 19, 21, 23, 25, 34, 36 -> AddonFailure.SOURCE_NOT_READY
                        2, 7, 16, 24, 28, 29, 30, 35 -> AddonFailure.INVALID_SOURCE
                        else -> if (response.code == 403) AddonFailure.ACCOUNT_REJECTED else AddonFailure.NETWORK
                    }
                    if (reason == AddonFailure.SOURCE_NOT_READY) retryAfterMillis = cooldown(response.header("Retry-After"), nowMillis())
                    throw AddonException(reason)
                }
                json
            }
        } catch (_: IOException) {
            throw AddonException(AddonFailure.NETWORK)
        }
    }

    companion object {
        private val API = "https://api.real-debrid.com/rest/1.0/".toHttpUrl()
        private val HASH = Regex("[a-f0-9]{40}")
        private val TORRENT_ID = Regex("[A-Za-z0-9_-]{1,100}")
        private val FAILED_STATUSES = setOf("magnet_error", "error", "virus", "dead")
        private const val POLL_ATTEMPTS = 6
        private const val POLL_INTERVAL_MILLIS = 3000L
        private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

        internal fun normalizedPath(value: String): String =
            Normalizer.normalize(value.trimStart('/'), Normalizer.Form.NFC)

        internal fun selectedFile(info: JSONObject, path: String): JSONObject {
            val files = info.optJSONArray("files") ?: throw AddonException(AddonFailure.INVALID_SOURCE)
            val wanted = normalizedPath(path)
            val matches = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .filter { normalizedPath(it.optString("path")) == wanted }
            return matches.singleOrNull()?.takeIf { it.optInt("id", -1) >= 0 }
                ?: throw AddonException(AddonFailure.INVALID_SOURCE)
        }

        internal fun singleSelectedLink(info: JSONObject, fileId: Int): String {
            val files = info.optJSONArray("files") ?: throw AddonException(AddonFailure.INVALID_SOURCE)
            val selected = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .filter { it.optInt("selected") == 1 }
            val links = info.optJSONArray("links") ?: throw AddonException(AddonFailure.INVALID_SOURCE)
            if (selected.size != 1 || selected.single().optInt("id", -1) != fileId || links.length() != 1) {
                throw AddonException(AddonFailure.INVALID_SOURCE)
            }
            return links.optString(0).takeIf { it.toHttpUrlOrNull()?.isHttps == true }
                ?: throw AddonException(AddonFailure.INVALID_SOURCE)
        }

        internal fun downloadUrl(value: String): HttpUrl {
            val url = value.toHttpUrlOrNull() ?: throw AddonException(AddonFailure.UNTRUSTED_HOST)
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) {
                throw AddonException(AddonFailure.UNTRUSTED_HOST)
            }
            return url
        }

        internal fun cooldown(header: String?, now: Long): Long {
            val seconds = header?.toLongOrNull()?.coerceIn(1, 24 * 60 * 60)
            val timestamp = if (seconds != null) now + seconds * 1000 else runCatching {
                ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull()
            return maxOf(now + 1000, timestamp ?: now + 60_000)
        }
    }
}
