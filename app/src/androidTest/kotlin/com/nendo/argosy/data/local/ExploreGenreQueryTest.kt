package com.nendo.argosy.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExploreGenreQueryTest {
    @Test fun genrePagesRespectSecondaryGenresPlatformOwnershipAndHiddenGames() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ALauncherDatabase::class.java
        ).build()
        try {
            db.platformDao().insertAll(listOf(
                PlatformEntity(1, "snes", name = "SNES", shortName = "SNES", romExtensions = "smc"),
                PlatformEntity(2, "n64", name = "N64", shortName = "N64", romExtensions = "z64")
            ))
            fun game(id: Long, platform: Long = 1, genres: String = "Puzzle,Adventure",
                     source: GameSource = GameSource.LOCAL_ONLY) = GameEntity(
                id = id, platformId = platform, title = "Test $id", sortTitle = "Test $id",
                localPath = if (source == GameSource.LOCAL_ONLY) "/test/$id" else null,
                rommId = null, igdbId = null, source = source,
                genre = "Puzzle", genres = genres, rating = 80f
            )
            db.gameDao().insertAll(listOf(
                game(1), game(2), game(3, platform = 2),
                game(4, genres = "Action-adventure"),
                game(5, source = GameSource.ROMM_REMOTE), game(6)
            ))
            db.userRomsHiddenDao().hide(null, 6)
            val first = db.gameDao().getExploreGenrePage("Adventure", listOf(1), false, null, 0, 2)
            val second = db.gameDao().getExploreGenrePage("Adventure", listOf(1), false, null, 2, 2)
            assertEquals(listOf(1L, 2L), first.map { it.id })
            assertEquals(listOf(5L), second.map { it.id })
            assertEquals(listOf(1L, 2L), db.gameDao()
                .getExploreGenrePage("Adventure", listOf(1), true, null, 0, 12).map { it.id })
        } finally {
            db.close()
        }
    }
}
