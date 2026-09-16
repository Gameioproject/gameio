package com.nendo.argosy.data.addon

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.DownloadResponse
import com.nendo.argosy.data.remote.romm.RomMResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonDownloadService @Inject constructor(
    private val addons: AddonRepository,
    private val format: AddonFormat,
    private val http: AddonHttpClient,
    private val debrid: AddonDebridResolver,
    private val preferences: UserPreferencesRepository
) {
    suspend fun open(sourceJson: String, range: String?): RomMResult<DownloadResponse> {
        val ownerContext = currentCoroutineContext()
        return withContext(Dispatchers.IO) {
            val match = format.parseMatch(sourceJson)
            val manifest = addons.manifestFor(match)
            format.validateMatch(match, manifest)
            val source = match.source
            val url = when (source.kind) {
                "internet_archive" -> archiveUrl(source.locator)
                "http" -> format.approvedUrl(source.locator.url!!, manifest.allowedHosts)
                "torrent" -> debrid.resolve(source)
                else -> throw AddonException(AddonFailure.INVALID_SOURCE)
            }
            val allowed: (HttpUrl) -> Boolean = { candidate ->
                candidate.isHttps && candidate.username.isEmpty() && candidate.password.isEmpty() &&
                    when (source.kind) {
                        "internet_archive" -> candidate.host == "archive.org" || candidate.host.endsWith(".archive.org")
                        "http" -> candidate.host in manifest.allowedHosts
                        else -> candidate.host == url.host
                    }
            }
            val response = http.open(url, range, ownerContext, allowed)
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code == 416) return@withContext RomMResult.Error("HTTP 416", 416)
                throw AddonException(statusFailure(code))
            }
            val body = response.body ?: run {
                response.close()
                throw AddonException(AddonFailure.NETWORK)
            }
            val partial = response.code == 206
            if (partial && !validContentRange(range, response.header("Content-Range"))) {
                response.close()
                throw AddonException(AddonFailure.INVALID_SOURCE)
            }
            val connections = preferences.userPreferences.first().downloadConnections
            val plan = parallelPlan(
                response.code, response.header("Accept-Ranges"), response.header("Content-Range"),
                body.contentLength(), connections
            )
            if (plan == null) return@withContext RomMResult.Success(DownloadResponse(body, partial))
            val spreadAcrossMirrors = source.kind == "internet_archive"
            val pieceUrl = if (spreadAcrossMirrors) url else response.request.url
            val contentType = body.contentType()
            response.close()
            val parallel = AddonParallelBody(contentType, plan.start, plan.end, ownerContext, connections) { from, to ->
                piece(pieceUrl, from, to, allowed)
            }
            RomMResult.Success(DownloadResponse(parallel, partial))
        }
    }

    private suspend fun piece(url: HttpUrl, from: Long, to: Long, allowed: (HttpUrl) -> Boolean): ByteArray {
        val response = http.open(url, "bytes=$from-$to", null, allowed)
        return response.use { reply ->
            if (reply.code != 206) throw AddonException(if (reply.isSuccessful) AddonFailure.INVALID_SOURCE else statusFailure(reply.code))
            val received = parseContentRange(reply.header("Content-Range"))
            if (received == null || received.start != from || received.end != to) throw AddonException(AddonFailure.INVALID_SOURCE)
            val length = (to - from + 1).toInt()
            val bytes = ByteArray(length)
            val input = reply.body?.byteStream() ?: throw AddonException(AddonFailure.NETWORK)
            var filled = 0
            while (filled < length) {
                val count = input.read(bytes, filled, length - filled)
                if (count < 0) throw AddonException(AddonFailure.NETWORK)
                filled += count
            }
            bytes
        }
    }

    internal data class ContentRange(val start: Long, val end: Long, val total: Long?)
    internal data class ParallelPlan(val start: Long, val end: Long)

    companion object {
        private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)")

        internal fun statusFailure(code: Int): AddonFailure = when (code) {
            401, 403 -> AddonFailure.ACCOUNT_REJECTED
            404 -> AddonFailure.NOT_FOUND
            else -> AddonFailure.NETWORK
        }

        internal fun parseContentRange(header: String?): ContentRange? {
            val match = header?.let { CONTENT_RANGE.matchEntire(it) } ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            if (end < start) return null
            return ContentRange(start, end, match.groupValues[3].toLongOrNull())
        }

        internal fun parallelPlan(
            code: Int,
            acceptRanges: String?,
            contentRange: String?,
            contentLength: Long,
            connections: Int
        ): ParallelPlan? {
            if (connections < 2) return null
            val plan = when (code) {
                206 -> parseContentRange(contentRange)?.let { range ->
                    range.total?.takeIf { it == range.end + 1 }?.let { ParallelPlan(range.start, range.end) }
                }
                200 -> if (acceptRanges.equals("bytes", ignoreCase = true) && contentLength > 0) ParallelPlan(0, contentLength - 1) else null
                else -> null
            } ?: return null
            return plan.takeIf { it.end - it.start + 1 >= AddonParallelBody.MIN_PARALLEL_BYTES }
        }

        internal fun archiveUrl(locator: AddonLocator): HttpUrl = "https://archive.org/download/".toHttpUrl()
            .newBuilder().addPathSegment(locator.item!!).apply {
                locator.path!!.split('/').forEach(::addPathSegment)
            }.build()

        internal fun validContentRange(requested: String?, received: String?): Boolean {
            val start = requested?.removePrefix("bytes=")?.removeSuffix("-")?.toLongOrNull() ?: return false
            val match = received?.let { Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)").matchEntire(it) } ?: return false
            val receivedStart = match.groupValues[1].toLongOrNull() ?: return false
            val receivedEnd = match.groupValues[2].toLongOrNull() ?: return false
            val total = match.groupValues[3].toLongOrNull()
            return start == receivedStart && receivedEnd >= receivedStart && (total == null || receivedEnd < total)
        }
    }
}
