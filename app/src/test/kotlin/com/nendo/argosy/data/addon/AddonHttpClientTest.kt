package com.nendo.argosy.data.addon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import com.nendo.argosy.data.remote.romm.RomMResult
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AddonHttpClientTest {
    private val server = MockWebServer()
    private val bodyStarted = CountDownLatch(1)
    private val callFailed = CountDownLatch(1)
    private lateinit var rawClient: OkHttpClient
    private lateinit var http: AddonHttpClient

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        rawClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false).followSslRedirects(false)
            .eventListener(object : EventListener() {
                override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
                override fun callFailed(call: Call, ioe: IOException) { callFailed.countDown() }
            }).build()
        http = AddonHttpClient(rawClient)
    }

    @After fun teardown() {
        rawClient.dispatcher.cancelAll()
        rawClient.connectionPool.evictAll()
        rawClient.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    @Test fun `approved HTTPS redirect returns one bounded shard without account headers`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/shard.json"))
        server.enqueue(MockResponse().setBody("{\"schemaVersion\":1,\"entries\":{}}"))
        val bytes = http.bytes(server.url("/start"), 128) { it.isHttps && it.host == "localhost" }
        assertEquals("{\"schemaVersion\":1,\"entries\":{}}", bytes.toString(Charsets.UTF_8))
        repeat(2) {
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test fun `redirect to an unapproved host is rejected before contacting it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://unapproved.invalid/shard.json"))
        try {
            http.bytes(server.url("/start"), 128) { it.host == "localhost" }
            fail("Expected host rejection")
        } catch (e: AddonException) { assertEquals(AddonFailure.UNTRUSTED_HOST, e.reason) }
        assertEquals(1, server.requestCount)
    }

    @Test fun `chunked body without content length cannot exceed shard limit`() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(33), 8))
        try {
            http.bytes(server.url("/shard.json"), 32) { true }
            fail("Expected size rejection")
        } catch (e: AddonException) { assertEquals(AddonFailure.TOO_LARGE, e.reason) }
    }

    @Test fun `leaving lookup cancels a stalled response body promptly`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").throttleBody(1, 5, TimeUnit.SECONDS))
        val request = async(Dispatchers.IO) { http.bytes(server.url("/slow"), 128) { true } }
        assertTrue("Body read did not start", bodyStarted.await(3, TimeUnit.SECONDS))
        withTimeout(1000) { request.cancelAndJoin() }
        assertTrue("Cancelled lookup left its socket open", callFailed.await(1, TimeUnit.SECONDS))
    }

    @Test fun `resume range survives an approved redirect`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", "/file"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("remaining"))
        http.open(server.url("/start"), "bytes=123-") { it.host == "localhost" }.use {
            assertEquals("remaining", it.body!!.string())
        }
        repeat(2) { assertEquals("bytes=123-", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Range")) }
    }

    @Test fun `cancelling a transfer after service open returns interrupts its stalled body`() = runBlocking {
        server.enqueue(MockResponse().setBody("remaining").throttleBody(1, 5, TimeUnit.SECONDS))
        val (service, sourceJson) = downloadService()
        val returned = CountDownLatch(1)
        val transfer = async(Dispatchers.IO) {
            val result = service.open(sourceJson, null)
            check(result is RomMResult.Success)
            returned.countDown()
            result.data.body.use { it.string() }
        }
        assertTrue("Service scope waited for escaped body", returned.await(3, TimeUnit.SECONDS))
        assertTrue("Body read did not start", bodyStarted.await(3, TimeUnit.SECONDS))
        withTimeout(1000) { transfer.cancelAndJoin() }
        assertTrue("Cancelled transfer left its socket open", callFailed.await(1, TimeUnit.SECONDS))
    }

    @Test fun `body close releases caller watcher after a temporary IO scope returns`() = runBlocking {
        server.enqueue(MockResponse().setBody("done"))
        val owner = currentCoroutineContext()
        val response = withTimeout(2000) {
            withContext(Dispatchers.IO) { http.open(server.url("/file"), ownerContext = owner) { true } }
        }
        response.use { assertEquals("done", it.body!!.string()) }
        assertFalse("Closed body retained a child job", owner.job.children.any())
    }

    @Test fun `a broken streaming response becomes an actionable network failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(1024)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        try {
            http.open(server.url("/broken")) { true }.use { it.body!!.string() }
            fail("Expected transfer failure")
        } catch (e: AddonException) { assertEquals(AddonFailure.NETWORK, e.reason) }
        assertFalse("Failed body retained a child job", currentCoroutineContext().job.children.any())
    }

    private fun downloadService(): Pair<AddonDownloadService, String> {
        val format = AddonFormat(Moshi.Builder().build())
        val manifest = AddonManifest(1, "test", "Test", "1", "catalog-shards-v1",
            AddonLookup("igdbId:platformSlug", "sha256-prefix-2", server.url("/").toString() + "{shard}.json"), listOf("localhost"))
        val source = AddonSource("one", "http", "Game.zip", AddonLocator(url = server.url("/file").toString()))
        val match = AddonSourceMatch("test", "Test", source, format.fingerprint(manifest), "123:snes")
        val addons = mockk<AddonRepository>()
        coEvery { addons.manifestFor(match) } returns manifest
        return AddonDownloadService(addons, format, http, mockk()) to format.encodeMatch(match)
    }
}
