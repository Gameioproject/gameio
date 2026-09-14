package com.nendo.argosy.data.addon

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.CoroutineContext

@Singleton
class AddonHttpClient internal constructor(private val client: OkHttpClient) {
    @Inject constructor() : this(OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build())

    suspend fun bytes(url: HttpUrl, maxBytes: Int, isAllowed: (HttpUrl) -> Boolean): ByteArray {
        require(maxBytes in 1..AddonFormat.MAX_SHARD_BYTES)
        var current = url
        repeat(6) { attempt ->
            if (!isAllowed(current)) throw AddonException(AddonFailure.UNTRUSTED_HOST)
            val result = client.newCall(Request.Builder().url(current).build()).readBounded(maxBytes)
            result.bytes?.let { return it }
            if (attempt == 5) throw AddonException(AddonFailure.NETWORK)
            current = result.location?.let(current::resolve) ?: throw AddonException(AddonFailure.NETWORK)
        }
        throw AddonException(AddonFailure.NETWORK)
    }

    suspend fun open(
        url: HttpUrl,
        range: String? = null,
        ownerContext: CoroutineContext? = null,
        isAllowed: (HttpUrl) -> Boolean
    ): Response {
        val lifetime = ownerContext ?: currentCoroutineContext()
        var current = url
        repeat(6) { attempt ->
            if (!isAllowed(current)) throw AddonException(AddonFailure.UNTRUSTED_HOST)
            val request = Request.Builder().url(current).apply {
                range?.let { header("Range", it) }
            }.build()
            val response = client.newCall(request).await(lifetime)
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            current = response.use {
                if (attempt == 5) throw AddonException(AddonFailure.NETWORK)
                it.header("Location")?.let(current::resolve)
                    ?: throw AddonException(AddonFailure.NETWORK)
            }
        }
        throw AddonException(AddonFailure.NETWORK)
    }
}

private data class BoundedResponse(val bytes: ByteArray? = null, val location: String? = null)

private suspend fun Call.readBounded(maxBytes: Int): BoundedResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    if (it.code in setOf(301, 302, 303, 307, 308)) {
                        BoundedResponse(location = it.header("Location"))
                    } else {
                        if (it.code == 404) throw AddonException(AddonFailure.NOT_FOUND)
                        if (!it.isSuccessful) throw AddonException(AddonFailure.NETWORK)
                        val body = it.body ?: throw AddonException(AddonFailure.NETWORK)
                        if (body.contentLength() > maxBytes) throw AddonException(AddonFailure.TOO_LARGE)
                        BoundedResponse(bytes = body.byteStream().readAddonBytes(maxBytes))
                    }
                }
                continuation.resume(result)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}

/**
 * The returned body retains cancellation from its caller until closed. The owner must be
 * captured before a temporary withContext scope, which cannot await the escaped body.
 */
internal suspend fun Call.await(ownerContext: CoroutineContext? = null): Response {
    val lifetime = ownerContext ?: currentCoroutineContext()
    val watcher = CoroutineScope(lifetime).launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@await.cancel()
        }
    }
    return try {
        awaitHeaders(watcher, lifetime)
    } catch (e: IOException) {
        watcher.cancel()
        lifetime.ensureActive()
        throw AddonException(AddonFailure.NETWORK, e)
    } catch (e: Throwable) {
        watcher.cancel()
        throw e
    }
}

private suspend fun Call.awaitHeaders(watcher: Job, lifetime: CoroutineContext): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body
            val wrapped = object : ResponseBody() {
                private val stream = object : ForwardingSource(body.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long = try {
                        super.read(sink, byteCount).also { if (it == -1L) watcher.cancel() }
                    } catch (e: IOException) {
                        watcher.cancel()
                        lifetime.ensureActive()
                        throw AddonException(AddonFailure.NETWORK, e)
                    } catch (e: Throwable) {
                        watcher.cancel()
                        throw e
                    }

                    override fun close() {
                        try { super.close() } finally { watcher.cancel() }
                    }
                }.buffer()
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = stream
            }
            continuation.resume(response.newBuilder().body(wrapped).build()) { _, value, _ -> value.close() }
        }
    })
}
