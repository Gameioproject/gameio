package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.GameCommentLike
import com.nendo.argosy.data.remote.romm.GameCommentModeration
import com.nendo.argosy.data.remote.romm.GameCommentReportWrite
import com.nendo.argosy.data.remote.romm.GameCommentWrite
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class GameCommentsFailure { NOT_CONNECTED, ACCOUNT_CHANGED, SIGN_IN, FORBIDDEN, NOT_FOUND, RATE_LIMIT, INVALID, UNAVAILABLE }

class GameCommentsException(val reason: GameCommentsFailure, val retryAfterSeconds: Int? = null) : IOException(reason.name)

class GameCommentsSession internal constructor(
    internal val api: RomMApi,
    val user: RomMUser,
    val serverUrl: String
)

@Singleton
class GameCommentsRepository @Inject constructor(private val connection: RomMConnectionManager) {
    fun isCurrentSession(session: GameCommentsSession): Boolean = connection.getApi() === session.api

    suspend fun openSession(): GameCommentsSession = withContext(Dispatchers.IO) {
        val api = connection.getApi() ?: throw GameCommentsException(GameCommentsFailure.NOT_CONNECTED)
        val serverUrl = connection.getBaseUrl()
        val user = response { api.getCurrentUser() }
        if (connection.getApi() !== api) throw GameCommentsException(GameCommentsFailure.ACCOUNT_CHANGED)
        GameCommentsSession(api, user, serverUrl)
    }

    suspend fun comments(session: GameCommentsSession, igdbId: Long, sort: String, offset: Int = 0) =
        request(session) { getGameComments(igdbId, sort, offset) }

    suspend fun replies(session: GameCommentsSession, commentId: Long, offset: Int = 0) =
        request(session) { getGameCommentReplies(commentId, offset) }

    suspend fun post(session: GameCommentsSession, igdbId: Long, body: GameCommentWrite) =
        request(session) { postGameComment(igdbId, body) }

    suspend fun edit(session: GameCommentsSession, commentId: Long, body: GameCommentWrite) =
        request(session) { editGameComment(commentId, body) }

    suspend fun like(session: GameCommentsSession, commentId: Long, liked: Boolean) =
        request(session) { likeGameComment(commentId, GameCommentLike(liked)) }

    suspend fun delete(session: GameCommentsSession, commentId: Long) =
        mutation(session) { deleteGameComment(commentId) }

    suspend fun report(session: GameCommentsSession, commentId: Long, reason: String) =
        mutation(session) { reportGameComment(commentId, GameCommentReportWrite(reason)) }

    suspend fun blocks(session: GameCommentsSession) = request(session) { getGameCommentBlocks() }

    suspend fun block(session: GameCommentsSession, userId: Long) = mutation(session) { blockGameCommentAuthor(userId) }

    suspend fun unblock(session: GameCommentsSession, userId: Long) = mutation(session) { unblockGameCommentAuthor(userId) }

    suspend fun reports(session: GameCommentsSession, offset: Int = 0) = request(session) { getGameCommentReports(offset) }

    suspend fun moderate(session: GameCommentsSession, reportId: Long, remove: Boolean) =
        mutation(session) { moderateGameComment(reportId, GameCommentModeration(if (remove) "remove" else "dismiss")) }

    suspend fun avatar(session: GameCommentsSession, userId: Long): ByteArray? = withContext(Dispatchers.IO) {
        verifySession(session)
        val result = session.api.getGameCommentAvatar(userId)
        if (!result.isSuccessful) { result.errorBody()?.close(); return@withContext null }
        val bytes = result.body()?.use { body ->
            val source = body.source()
            val limit = 2L * 1024 * 1024
            if (source.request(limit + 1)) null else source.readByteArray()
        }
        verifySession(session)
        bytes
    }

    private suspend fun <T> request(session: GameCommentsSession, block: suspend RomMApi.() -> Response<T>): T =
        withContext(Dispatchers.IO) {
            verifySession(session)
            val result = response { block(session.api) }
            verifySession(session)
            result
        }

    private suspend fun mutation(session: GameCommentsSession, block: suspend RomMApi.() -> Response<Unit>) =
        withContext(Dispatchers.IO) {
            verifySession(session)
            response { block(session.api).let { result ->
                if (result.isSuccessful) Response.success(Unit) else result
            } }
            verifySession(session)
        }

    private fun verifySession(session: GameCommentsSession) {
        if (connection.getApi() !== session.api) throw GameCommentsException(GameCommentsFailure.ACCOUNT_CHANGED)
    }

    private suspend fun <T> response(block: suspend () -> Response<T>): T {
        try {
            val response = block()
            if (response.isSuccessful) return response.body() ?: throw GameCommentsException(GameCommentsFailure.UNAVAILABLE)
            val reason = when (response.code()) {
                401 -> GameCommentsFailure.SIGN_IN
                403 -> GameCommentsFailure.FORBIDDEN
                404 -> GameCommentsFailure.NOT_FOUND
                400, 422 -> GameCommentsFailure.INVALID
                429 -> GameCommentsFailure.RATE_LIMIT
                else -> GameCommentsFailure.UNAVAILABLE
            }
            response.errorBody()?.close()
            throw GameCommentsException(reason, response.headers()["Retry-After"]?.toIntOrNull())
        } catch (error: CancellationException) {
            throw error
        } catch (error: GameCommentsException) {
            throw error
        } catch (_: Exception) {
            throw GameCommentsException(GameCommentsFailure.UNAVAILABLE)
        }
    }
}
