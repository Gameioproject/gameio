package com.nendo.argosy.ui.screens.home

import androidx.annotation.StringRes
import com.nendo.argosy.R

enum class ExploreGenre(val token: String, @StringRes val titleRes: Int) {
    SPORTS("Sport", R.string.explore_sports),
    ADVENTURE("Adventure", R.string.explore_adventure),
    RACING("Racing", R.string.explore_racing),
    RPG("Role-playing (RPG)", R.string.explore_rpg),
    PLATFORM("Platform", R.string.explore_platform),
    PUZZLE("Puzzle", R.string.explore_puzzle),
    FIGHTING("Fighting", R.string.explore_fighting),
    SHOOTER("Shooter", R.string.explore_shooter),
    ARCADE("Arcade", R.string.explore_arcade),
    STRATEGY("Strategy", R.string.explore_strategy),
    SIMULATION("Simulator", R.string.explore_simulation),
    MUSIC("Music", R.string.explore_music),
    BEAT_EM_UP("Hack and slash/Beat 'em up", R.string.explore_beat_em_up),
    TACTICAL("Tactical", R.string.explore_tactical),
    CARD_BOARD("Card & Board Game", R.string.explore_card_board),
    INDIE("Indie", R.string.explore_indie)
}

data class ExplorePage(val games: List<HomeGameUi>, val hasMore: Boolean)

data class ExploreState(
    val rows: List<DiscoverySection> = emptyList(),
    val cursor: Int = 0,
    val finishedGenres: Set<ExploreGenre> = emptySet(),
    val seen: Map<ExploreGenre, Set<Long>> = emptyMap(),
    val loading: Boolean = false,
    val failed: Boolean = false
) {
    val exhausted: Boolean get() = finishedGenres.size == ExploreGenre.entries.size
    val genre: ExploreGenre get() = ExploreGenre.entries[cursor % ExploreGenre.entries.size]
    val page: Int get() = cursor / ExploreGenre.entries.size

    fun append(result: ExplorePage): ExploreState {
        val unique = result.games.distinctBy { it.id }.filter { it.id !in seen[genre].orEmpty() }
        val section = DiscoverySection(
            "genre-${genre.name}-$page", genre.titleRes, unique, R.string.discovery_empty_catalog,
            continuation = page > 0
        )
        return copy(
            rows = if (unique.isEmpty()) rows else rows + section,
            cursor = cursor + 1,
            finishedGenres = if (result.hasMore) finishedGenres else finishedGenres + genre,
            seen = seen + (genre to (seen[genre].orEmpty() + unique.map { it.id }))
        )
    }
}
