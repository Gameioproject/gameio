package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import javax.inject.Inject

class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker
) {
    suspend operator fun invoke(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        prefetchedGame: GameEntity? = null
    ): LaunchResult {
        val result = gameLauncher.launch(gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame)
        if (result is LaunchResult.Success) {
            val coreName = extractCoreName(result.intent)
            // The built-in emulator's intent targets this app, but the session has to name the
            // emulator, not the launcher: every resolver keys on the synthetic built-in package,
            // and a session recorded under the app's own id ends without a save or state sync.
            val targetPackage = result.intent.component?.packageName
                ?: result.intent.`package`
                ?: ""
            val emulatorPackage = if (targetPackage == com.nendo.argosy.BuildConfig.APPLICATION_ID) {
                com.nendo.argosy.data.emulator.EmulatorRegistry.BUILTIN_PACKAGE
            } else {
                targetPackage
            }
            playSessionTracker.startSession(
                gameId = gameId,
                emulatorPackage = emulatorPackage,
                coreName = coreName,
                isNewGame = !forResume,
                variantFileId = variantFileId
            )
        }
        return result
    }

    private fun extractCoreName(intent: Intent): String? {
        val libretroPath = intent.getStringExtra("LIBRETRO") ?: return null
        val coreFile = libretroPath.substringAfterLast("/")
        return coreFile
            .removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
            .takeIf { it.isNotEmpty() }
    }
}
