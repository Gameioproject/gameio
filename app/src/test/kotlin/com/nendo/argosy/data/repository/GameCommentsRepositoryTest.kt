package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class GameCommentsRepositoryTest {
    private val connection = mockk<RomMConnectionManager>()
    private val api = mockk<RomMApi>()
    private val repository = GameCommentsRepository(connection)
    private val user = RomMUser(1, "Player", true, "admin")

    @Test fun `write is bound to captured server connection and rejected after account switch`() = runTest {
        every { connection.getApi() } returns api
        every { connection.getBaseUrl() } returns "https://game.test"
        coEvery { api.getCurrentUser() } returns Response.success(user)
        val session = repository.openSession()
        every { connection.getApi() } returns mockk<RomMApi>()
        try { repository.post(session, 10, GameCommentWrite("hello")); fail("account change must fail") }
        catch (error: GameCommentsException) { assertEquals(GameCommentsFailure.ACCOUNT_CHANGED, error.reason) }
        coVerify(exactly = 0) { api.postGameComment(any(), any()) }
    }

    @Test fun `rate limit response preserves retry delay without exposing server body`() = runTest {
        every { connection.getApi() } returns api
        val session = GameCommentsSession(api, user, "https://game.test")
        val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url("https://game.test").build()).protocol(okhttp3.Protocol.HTTP_1_1).code(429).message("Too many requests").headers(Headers.headersOf("Retry-After", "30")).build()
        coEvery { api.postGameComment(any(), any()) } returns Response.error("private diagnostics".toResponseBody(), raw)
        try { repository.post(session, 10, GameCommentWrite("hello")); fail("must rate limit") }
        catch (error: GameCommentsException) {
            assertEquals(GameCommentsFailure.RATE_LIMIT, error.reason)
            assertEquals(30, error.retryAfterSeconds)
            assertFalse(error.message.orEmpty().contains("private diagnostics"))
        }
    }

    @Test fun `avatar request uses authenticated user route and bounds response bytes`() = runTest {
        every { connection.getApi() } returns api
        val session = GameCommentsSession(api, user, "https://game.test")
        coEvery { api.getGameCommentAvatar(3) } returns Response.success("avatar".toResponseBody())
        assertArrayEquals("avatar".toByteArray(), repository.avatar(session, 3))
        coEvery { api.getGameCommentAvatar(3) } returns Response.success(ByteArray(2 * 1024 * 1024 + 1).toResponseBody())
        assertNull(repository.avatar(session, 3))
    }
}
