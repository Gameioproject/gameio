package com.nendo.argosy.data.addon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.CoroutineContext

/**
 * Streams bytes [start]..[end] in order while fetching fixed-size pieces over several
 * connections. At most [connections] + 2 pieces are held in memory, so a slow disk
 * back-pressures the network instead of growing the heap, and the consumer still sees
 * one sequential stream that the download queue can resume by file length.
 */
internal class AddonParallelBody(
    private val contentType: MediaType?,
    private val start: Long,
    private val end: Long,
    private val ownerContext: CoroutineContext,
    private val connections: Int,
    private val pieceBytes: Int = pieceSize(connections),
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
    private val fetch: suspend (from: Long, to: Long) -> ByteArray
) : ResponseBody() {
    private val job = SupervisorJob(ownerContext[Job])
    private val pieces = LinkedBlockingQueue<CompletableFuture<ByteArray>>()
    private val slots = Semaphore(connections + 2)
    private val stream: BufferedSource = PieceSource().buffer()

    init {
        require(end >= start && connections > 0 && pieceBytes > 0)
        job.invokeOnCompletion { cause ->
            if (cause != null) pieces.offer(CompletableFuture<ByteArray>().apply { completeExceptionally(cause) })
        }
        CoroutineScope(Dispatchers.IO + job).launch {
            val inFlight = Semaphore(connections)
            var from = start
            while (from <= end) {
                val to = minOf(end, from + pieceBytes - 1)
                val piece = CompletableFuture<ByteArray>()
                slots.acquire()
                pieces.put(piece)
                val first = from
                launch {
                    try {
                        piece.complete(inFlight.withPermit { fetchWithRetry(first, to) })
                    } catch (e: Throwable) {
                        piece.completeExceptionally(e)
                        if (e !is CancellationException) job.cancel(CancellationException("piece $first-$to failed", e))
                    }
                }
                from = to + 1
            }
            pieces.put(END)
        }
    }

    override fun contentType(): MediaType? = contentType
    override fun contentLength(): Long = end - start + 1
    override fun source(): BufferedSource = stream

    private suspend fun fetchWithRetry(from: Long, to: Long): ByteArray {
        var attempt = 0
        while (true) {
            try {
                val bytes = fetch(from, to)
                if (bytes.size.toLong() != to - from + 1) throw AddonException(AddonFailure.INVALID_SOURCE)
                return bytes
            } catch (e: AddonException) {
                attempt++
                if (e.reason != AddonFailure.NETWORK || attempt >= RETRIES) throw e
                delay(retryDelayMillis shl (attempt - 1))
            }
        }
    }

    private inner class PieceSource : Source {
        private var current = ByteArray(0)
        private var offset = 0
        private var finished = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (finished) return -1
            if (offset == current.size) {
                if (current.isNotEmpty()) slots.release()
                val next = pieces.take()
                if (next === END) {
                    finished = true
                    job.cancel()
                    return -1
                }
                current = try {
                    next.get()
                } catch (e: ExecutionException) {
                    throw failure(e.cause ?: e)
                } catch (e: CancellationException) {
                    throw failure(e)
                }
                offset = 0
            }
            val count = minOf(byteCount, (current.size - offset).toLong()).toInt()
            sink.write(current, offset, count)
            offset += count
            return count.toLong()
        }

        private fun failure(cause: Throwable): Throwable {
            close()
            ownerContext.ensureActive()
            return cause as? AddonException ?: AddonException(AddonFailure.NETWORK, cause)
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            job.cancel()
        }
    }

    companion object {
        const val MIN_PARALLEL_BYTES = 16L * 1024 * 1024
        private const val RETRIES = 6
        private const val RETRY_DELAY_MILLIS = 1000L
        private val END = CompletableFuture<ByteArray>()

        internal fun pieceSize(connections: Int): Int =
            (48L * 1024 * 1024 / connections).coerceIn(2L * 1024 * 1024, 4L * 1024 * 1024).toInt()
    }
}
