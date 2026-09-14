package com.nendo.argosy.data.addon

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AddonDebridResolverTest {
    private val source = AddonSource("source-1", "torrent", "Game.zip",
        AddonLocator(infoHash = "a".repeat(40), fileIndex = 7, path = "Folder/Game.zip"))

    @Test fun `requires own account before contacting service`() = runTest {
        val credentials = credentials(null)
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials, requests)
        assertFailure(AddonFailure.ACCOUNT_REQUIRED) { resolver.resolve(source) }
        assertTrue(requests.isEmpty())
    }

    @Test fun `validates premium account before persisting trimmed token`() = runTest {
        val credentials = credentials(null)
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials, requests, Reply(200, """{"type":"premium"}"""))
        resolver.setToken("  user-owned-token  ")
        coVerify(exactly = 1) { credentials.save("user-owned-token") }
        assertEquals("Bearer user-owned-token", requests.single().header("Authorization"))
        assertEquals("https://api.real-debrid.com/rest/1.0/user", requests.single().url.toString())
    }

    @Test fun `rejected account never overwrites saved credentials`() = runTest {
        for (response in listOf(Reply(401), Reply(403), Reply(200, """{"type":"free"}"""))) {
            val credentials = credentials()
            val resolver = resolver(credentials, mutableListOf(), response)
            assertFailure(AddonFailure.ACCOUNT_REJECTED) { resolver.setToken("new-token") }
            coVerify(exactly = 0) { credentials.save(any()) }
        }
    }

    @Test fun `invalid token characters never become request headers`() = runTest {
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials(), requests)
        assertFailure(AddonFailure.ACCOUNT_REJECTED) { resolver.setToken("token\r\nHeader:secret") }
        assertFailure(AddonFailure.ACCOUNT_REJECTED) { resolver.setToken(" ") }
        assertTrue(requests.isEmpty())
        assertFalse(AddonCredentials.validToken("x".repeat(2049)))
    }

    @Test fun `selects exact service file id and never first link`() = runTest {
        val credentials = credentials()
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials, requests,
            Reply(201, """{"id":"torrent-1"}"""),
            Reply(200, info("waiting_files_selection", false)),
            Reply(204), Reply(200, info("downloaded", true)),
            Reply(200, """{"download":"https://download.real-debrid.com/Game.zip"}"""))
        val url = resolver.resolve(source)
        assertEquals("https://download.real-debrid.com/Game.zip", url.toString())
        val add = requests[0].body as FormBody
        assertEquals("magnet:?xt=urn:btih:${"a".repeat(40)}&so=7", add.value(0))
        val select = requests[2].body as FormBody
        assertEquals("files", select.name(0))
        assertEquals("42", select.value(0))
        assertEquals("https://real-debrid.com/d/wanted", (requests.last().body as FormBody).value(0))
        assertTrue(requests.all { it.url.host == "api.real-debrid.com" && it.header("Authorization") == "Bearer user-token" })
        coVerify { credentials.rememberTorrent(any(), "torrent-1") }
    }

    @Test fun `existing torrent survives restart without duplicate magnet`() = runTest {
        val credentials = credentials()
        coEvery { credentials.torrentId(any()) } returns "persisted-id"
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials, requests, Reply(200, info("downloaded", true)),
            Reply(200, """{"download":"https://download.real-debrid.com/Game.zip"}"""))
        resolver.resolve(source)
        assertEquals(2, requests.size)
        assertTrue(requests.first().url.encodedPath.endsWith("/torrents/info/persisted-id"))
        assertFalse(requests.any { it.url.encodedPath.endsWith("addMagnet") })
    }

    @Test fun `not ready source stops after bounded polls and preserves torrent for retry`() = runTest {
        val credentials = credentials()
        coEvery { credentials.torrentId(any()) } returns "still-downloading"
        val requests = mutableListOf<Request>()
        val resolver = resolver(credentials, requests,
            *List(6) { Reply(200, info("downloading", true)) }.toTypedArray())
        assertFailure(AddonFailure.SOURCE_NOT_READY) { resolver.resolve(source) }
        assertEquals(6, requests.size)
        coVerify(exactly = 0) { credentials.rememberTorrent(any(), any()) }
    }

    @Test fun `mismatched torrent hash cannot return another game`() = runTest {
        val credentials = credentials()
        coEvery { credentials.torrentId(any()) } returns "wrong-id"
        val requests = mutableListOf<Request>()
        val body = JSONObject(info("downloaded", true)).put("hash", "b".repeat(40)).toString()
        val resolver = resolver(credentials, requests, Reply(200, body))
        assertFailure(AddonFailure.INVALID_SOURCE) { resolver.resolve(source) }
        assertEquals(1, requests.size)
        coVerify { credentials.rememberTorrent(any(), null) }
    }

    @Test fun `rate limit stops retries until Retry-After passes`() = runTest {
        var now = 10_000L
        val requests = mutableListOf<Request>()
        val credentials = credentials()
        val client = client(requests, listOf(Reply(429, retryAfter = "120"), Reply(200, """{"type":"premium"}""")))
        val resolver = AddonDebridResolver(credentials, client) { now }
        assertFailure(AddonFailure.SOURCE_NOT_READY) { resolver.setToken("new-token") }
        now += 119_000
        assertFailure(AddonFailure.SOURCE_NOT_READY) { resolver.setToken("new-token") }
        assertEquals(1, requests.size)
        now += 1001
        resolver.setToken("new-token")
        assertEquals(2, requests.size)
    }

    @Test fun `network errors and redirects never persist token`() = runTest {
        for (replies in listOf(emptyList(), listOf(Reply(302, "redirect")))) {
            val credentials = credentials()
            val resolver = resolver(credentials, mutableListOf(), *replies.toTypedArray())
            assertFailure(AddonFailure.NETWORK) { resolver.setToken("new-token") }
            coVerify(exactly = 0) { credentials.save(any()) }
        }
    }

    @Test fun `account traffic limit is retryable instead of rejecting credentials`() = runTest {
        val credentials = credentials()
        val resolver = resolver(credentials, mutableListOf(), Reply(403, """{"error":"traffic_exhausted","error_code":23}"""))
        assertFailure(AddonFailure.SOURCE_NOT_READY) { resolver.setToken("user-token") }
        coVerify(exactly = 0) { credentials.save(any()) }
    }

    @Test fun `path matching normalizes unicode but never falls back to filename`() {
        val info = JSONObject("""{"files":[{"id":3,"path":"/Folder/Cafe\u0301.zip"},{"id":4,"path":"/Other/Caf\u00e9.zip"}]}""")
        assertEquals(3, AddonDebridResolver.selectedFile(info, "Folder/Café.zip").getInt("id"))
        try {
            AddonDebridResolver.selectedFile(info, "Missing/Café.zip")
            fail("A filename alone must not select a different edition")
        } catch (e: AddonException) { assertEquals(AddonFailure.INVALID_SOURCE, e.reason) }
    }

    @Test fun `multiple selected files or split links are not guessed`() {
        for (json in listOf(
            """{"files":[{"id":1,"selected":1},{"id":2,"selected":1}],"links":["https://host/1","https://host/2"]}""",
            """{"files":[{"id":1,"selected":1}],"links":["https://host/1","https://host/2"]}""",
            """{"files":[{"id":2,"selected":1}],"links":["https://host/2"]}""")) {
            try { AddonDebridResolver.singleSelectedLink(JSONObject(json), 1); fail("Expected invalid selection") }
            catch (e: AddonException) { assertEquals(AddonFailure.INVALID_SOURCE, e.reason) }
        }
    }

    @Test fun `unrestricted URL rejects embedded credentials and plain HTTP`() {
        for (url in listOf("http://downloads.example/game.zip", "https://secret@downloads.example/game.zip", "https://downloads.example/game.zip#frag")) {
            try { AddonDebridResolver.downloadUrl(url); fail("Expected invalid download") }
            catch (e: AddonException) { assertEquals(AddonFailure.UNTRUSTED_HOST, e.reason) }
        }
        assertEquals(120_000L, AddonDebridResolver.cooldown("120", 0))
        assertEquals(60_000L, AddonDebridResolver.cooldown("nonsense", 0))
        assertEquals(120_000L, AddonDebridResolver.cooldown("Thu, 1 Jan 1970 00:02:00 GMT", 0))
    }

    private fun credentials(token: String? = "user-token") = mockk<AddonCredentials> {
        every { hasAccount } returns MutableStateFlow(token != null)
        coEvery { token() } returns token
        coEvery { torrentId(any()) } returns null
        coEvery { rememberTorrent(any(), any()) } returns Unit
        coEvery { save(any()) } returns Unit
        coEvery { clear() } returns Unit
    }

    private fun resolver(credentials: AddonCredentials, requests: MutableList<Request>, vararg replies: Reply) =
        AddonDebridResolver(credentials, client(requests, replies.toList())) { 0L }

    private fun client(requests: MutableList<Request>, replies: List<Reply>) = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            requests.add(chain.request())
            val reply = replies.getOrNull(requests.lastIndex) ?: throw IOException("Test connection unavailable")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(reply.code).message("Test reply").body(reply.body.toResponseBody()).apply {
                    reply.retryAfter?.let { header("Retry-After", it) }
                }.build()
        }.build()

    private fun info(status: String, selected: Boolean) = """{"hash":"${"a".repeat(40)}","status":"$status","files":[{"id":1,"path":"/Other/Game.zip","selected":0},{"id":42,"path":"/Folder/Game.zip","selected":${if (selected) 1 else 0}}],"links":["https://real-debrid.com/d/wanted"]}"""

    private suspend fun assertFailure(reason: AddonFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $reason") }
        catch (e: AddonException) { assertEquals(reason, e.reason) }
    }

    private data class Reply(val code: Int, val body: String = "{}", val retryAfter: String? = null)
}
