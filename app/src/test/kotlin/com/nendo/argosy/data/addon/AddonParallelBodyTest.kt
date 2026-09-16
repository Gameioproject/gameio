package com.nendo.argosy.data.addon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class AddonParallelBodyTest {
    private val file = ByteArray(1_000_003) { (it * 31 + it / 977).toByte() }

    private fun body(owner: CoroutineContext, connections: Int = 4, pieceBytes: Int = 65_536, start: Long = 0,
                     fetch: suspend (Long, Long) -> ByteArray = { from, to -> file.copyOfRange(from.toInt(), to.toInt() + 1) }) =
        AddonParallelBody(null, start, file.size - 1L, owner, connections, pieceBytes, retryDelayMillis = 1, fetch = fetch)

    @Test fun `pieces fetched concurrently arrive in order and byte-exact`() = runBlocking {
        val active = AtomicInteger(); val peak = AtomicInteger()
        val body = body(coroutineContext, connections = 4) { from, to ->
            val now = active.incrementAndGet(); peak.accumulateAndGet(now, ::maxOf)
            delay((to % 7) + 1)
            active.decrementAndGet()
            file.copyOfRange(from.toInt(), to.toInt() + 1)
        }
        val received = withContext(Dispatchers.IO) { body.use { it.byteStream().readBytes() } }
        assertArrayEquals(file, received)
        assertEquals(file.size.toLong(), body.contentLength())
        assertTrue("Expected concurrent fetches, peak was ${peak.get()}", peak.get() > 1)
        assertTrue("Never more than the configured connections", peak.get() <= 4)
    }

    @Test fun `resumed body starts at the requested offset`() = runBlocking {
        val body = body(coroutineContext, start = 123_456)
        val received = withContext(Dispatchers.IO) { body.use { it.byteStream().readBytes() } }
        assertArrayEquals(file.copyOfRange(123_456, file.size), received)
    }

    @Test fun `a slow consumer bounds pieces held in memory`() = runBlocking {
        val started = AtomicInteger()
        val body = body(coroutineContext, connections = 2, pieceBytes = 10_000) { from, to ->
            started.incrementAndGet(); file.copyOfRange(from.toInt(), to.toInt() + 1)
        }
        withContext(Dispatchers.IO) {
            val stream = body.byteStream()
            stream.read(ByteArray(10_000))
            delay(200)
            assertTrue("Started ${started.get()} pieces for a stalled reader", started.get() <= 2 + 2 + 1)
            body.close()
        }
    }

    @Test fun `transient network failures retry and permanent ones do not`() = runBlocking {
        val attempts = AtomicInteger()
        val flaky = body(coroutineContext, connections = 2) { from, to ->
            if (from == 65_536L && attempts.incrementAndGet() <= 2) throw AddonException(AddonFailure.NETWORK)
            file.copyOfRange(from.toInt(), to.toInt() + 1)
        }
        assertArrayEquals(file, withContext(Dispatchers.IO) { flaky.use { it.byteStream().readBytes() } })
        assertEquals("two failures then one success", 3, attempts.get())

        val calls = AtomicInteger()
        val broken = body(coroutineContext, connections = 2) { from, to ->
            if (from == 65_536L) { calls.incrementAndGet(); throw AddonException(AddonFailure.INVALID_SOURCE) }
            file.copyOfRange(from.toInt(), to.toInt() + 1)
        }
        try {
            withContext(Dispatchers.IO) { broken.use { it.byteStream().readBytes() } }
            fail("Expected failure")
        } catch (e: AddonException) { assertEquals(AddonFailure.INVALID_SOURCE, e.reason) }
        assertEquals(1, calls.get())
        withTimeout(2000) { while (coroutineContext[Job]!!.children.any()) delay(10) }
    }

    @Test fun `cancelling the owner stops fetching and unblocks the reader`() = runBlocking {
        val transfer = launch(Dispatchers.IO) {
            val body = body(coroutineContext, connections = 2) { from, to -> delay(50); file.copyOfRange(from.toInt(), to.toInt() + 1) }
            body.use { it.byteStream().readBytes() }
        }
        delay(120)
        transfer.cancel()
        withTimeout(2000) { transfer.join() }
        assertTrue(transfer.isCancelled)
    }

    @Test fun `plan requires proven range support and a large remainder`() {
        val big = 64L * 1024 * 1024
        assertEquals(AddonDownloadService.ParallelPlan(0, big - 1), AddonDownloadService.parallelPlan(200, "bytes", null, big, 4))
        assertNull("No Accept-Ranges", AddonDownloadService.parallelPlan(200, null, null, big, 4))
        assertNull("Single connection", AddonDownloadService.parallelPlan(200, "bytes", null, big, 1))
        assertNull("Too small", AddonDownloadService.parallelPlan(200, "bytes", null, 1024, 4))
        assertEquals(AddonDownloadService.ParallelPlan(1000, big - 1), AddonDownloadService.parallelPlan(206, null, "bytes 1000-${big - 1}/$big", big - 1000, 8))
        assertNull("Partial that does not run to the end", AddonDownloadService.parallelPlan(206, null, "bytes 1000-5000/$big", 4001, 8))
        assertNull("Unknown total", AddonDownloadService.parallelPlan(206, null, "bytes 1000-${big - 1}/*", big - 1000, 8))
        assertEquals(AddonDownloadService.ContentRange(5, 9, 100), AddonDownloadService.parseContentRange("bytes 5-9/100"))
        assertNull(AddonDownloadService.parseContentRange("bytes 9-5/100"))
        assertEquals(4 * 1024 * 1024, AddonParallelBody.pieceSize(4))
        assertEquals(3 * 1024 * 1024, AddonParallelBody.pieceSize(16))
    }

    @Test fun `owner cancellation surfaces as cancellation not a network error`() = runBlocking {
        val job = launch(Dispatchers.IO) {
            val body = body(coroutineContext, connections = 2) { _, _ -> delay(10_000); ByteArray(0) }
            try {
                body.use { it.byteStream().readBytes() }
            } catch (e: AddonException) {
                fail("Cancellation was reported as ${e.reason}")
            } catch (e: CancellationException) {
                throw e
            }
        }
        delay(100)
        job.cancel()
        withTimeout(2000) { job.join() }
    }
}
