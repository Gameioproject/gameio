package com.nendo.argosy.data.addon

import com.nendo.argosy.data.remote.romm.DownloadResponse
import com.nendo.argosy.data.remote.romm.RomMResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonDownloadService @Inject constructor(
    private val addons: AddonRepository,
    private val format: AddonFormat,
    private val http: AddonHttpClient,
    private val debrid: AddonDebridResolver
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
            val response = http.open(url, range, ownerContext) { candidate ->
                candidate.isHttps && candidate.username.isEmpty() && candidate.password.isEmpty() &&
                    when (source.kind) {
                        "internet_archive" -> candidate.host == "archive.org" || candidate.host.endsWith(".archive.org")
                        "http" -> candidate.host in manifest.allowedHosts
                        else -> candidate.host == url.host
                    }
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code == 416) return@withContext RomMResult.Error("HTTP 416", 416)
                throw AddonException(when (code) {
                    401, 403 -> AddonFailure.ACCOUNT_REJECTED
                    404 -> AddonFailure.NOT_FOUND
                    else -> AddonFailure.NETWORK
                })
            }
            val body = response.body ?: run {
                response.close()
                throw AddonException(AddonFailure.NETWORK)
            }
            if (response.code == 206 && !validContentRange(range, response.header("Content-Range"))) {
                response.close()
                throw AddonException(AddonFailure.INVALID_SOURCE)
            }
            RomMResult.Success(DownloadResponse(body, response.code == 206))
        }
    }

    companion object {
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
