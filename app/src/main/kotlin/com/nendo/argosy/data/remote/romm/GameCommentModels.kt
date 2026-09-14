package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val GAME_COMMENT_MAX_LENGTH = 2000
const val GAME_COMMENT_REPORT_MAX_LENGTH = 500

@JsonClass(generateAdapter = true)
data class GameCommentAuthor(
    val id: Long,
    val username: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GameComment(
    val id: Long,
    @Json(name = "igdb_id") val igdbId: Long,
    @Json(name = "parent_id") val parentId: Long? = null,
    val author: GameCommentAuthor? = null,
    val body: String,
    val spoiler: Boolean = false,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    @Json(name = "like_count") val likeCount: Int = 0,
    @Json(name = "reply_count") val replyCount: Int = 0,
    val liked: Boolean = false,
    @Json(name = "can_edit") val canEdit: Boolean = false,
    @Json(name = "can_delete") val canDelete: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GameCommentPage(val items: List<GameComment>, val total: Int, val limit: Int, val offset: Int)

@JsonClass(generateAdapter = true)
data class GameCommentWrite(
    val body: String,
    val spoiler: Boolean = false,
    @Json(name = "parent_id") val parentId: Long? = null
)

@JsonClass(generateAdapter = true)
data class GameCommentLike(val liked: Boolean)

@JsonClass(generateAdapter = true)
data class GameCommentReportWrite(val reason: String)

@JsonClass(generateAdapter = true)
data class GameCommentModeration(val action: String)

@JsonClass(generateAdapter = true)
data class GameCommentReport(
    val id: Long,
    @Json(name = "comment_id") val commentId: Long,
    @Json(name = "game_title") val gameTitle: String = "",
    @Json(name = "igdb_id") val igdbId: Long,
    val reporter: GameCommentAuthor,
    val author: GameCommentAuthor? = null,
    val reason: String,
    @Json(name = "body_snapshot") val bodySnapshot: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class GameCommentReportPage(val items: List<GameCommentReport>, val total: Int, val limit: Int, val offset: Int)
