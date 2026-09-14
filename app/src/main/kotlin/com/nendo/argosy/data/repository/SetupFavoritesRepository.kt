package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMLibrarySyncService
import javax.inject.Inject

/**
 * Metadata only: picking favorites never looks up a download source.
 */
class SetupFavoritesRepository @Inject constructor(
    private val games: GameRepository,
    private val librarySync: RomMLibrarySyncService,
    private val preferences: UserPreferencesRepository
) {
    suspend fun favorites(): List<GameEntity> = games.getFavorites()

    suspend fun page(query: String, offset: Int, limit: Int): List<GameEntity> {
        val params = if (query.isBlank()) {
            mapOf("order_by" to "rating", "order_dir" to "desc", "min_rating" to "75")
        } else {
            mapOf("search_term" to query.trim())
        }
        val ids = librarySync.fetchRomsByParams(params, limit, offset, strict = true)
        val byId = games.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull(byId::get)
    }

    suspend fun setFavorite(gameId: Long, favorite: Boolean) {
        preferences.clearRecommendations()
        games.updateFavoriteWithSync(gameId, favorite)
    }
}
