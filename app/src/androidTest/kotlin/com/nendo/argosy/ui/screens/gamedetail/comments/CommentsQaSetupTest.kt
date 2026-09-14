package com.nendo.argosy.ui.screens.gamedetail.comments

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.MainActivity
import com.nendo.argosy.data.preferences.dataStore
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMUser
import com.nendo.argosy.data.remote.romm.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI

/** Explicit local integration setup, skipped by every normal instrumentation run. */
@RunWith(AndroidJUnit4::class)
class CommentsQaSetupTest {
    @Test
    fun connectFreshInstallToIsolatedCommentsServer() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("gameioQaSetup") == "true")
        check(BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = URI(requireNotNull(args.getString("gameioQaUrl")))
        check(url.scheme == "http" && url.host in setOf("127.0.0.1", "10.0.2.2") && url.port == 19009)
        check(url.userInfo == null && url.rawQuery == null && url.rawFragment == null && url.path in listOf("", "/"))
        val role = args.getString("gameioQaRole") ?: "player"
        check(role in setOf("admin", "player", "other"))
        val fixture = File(context.filesDir, "gameio-comments-qa.json")
        check(fixture.isFile && fixture.length() in 1L..8192L)
        val account = JSONObject(fixture.readText()).getJSONObject(role)
        val username = account.getString("username")
        check(username == "qa_comments_$role")
        val before = context.dataStore.data.first()
        val storedUrl = before[stringPreferencesKey("romm_url")]
        val storedToken = before[stringPreferencesKey("romm_token")]
        val storedUserId = before[longPreferencesKey("romm_user_id")]
        val freshInstall = storedUserId == null && storedToken.isNullOrBlank() && storedUrl.isNullOrBlank()
        val sameQaAccount = storedUserId != null && !storedToken.isNullOrBlank() &&
            storedUrl?.trimEnd('/') == url.toString().trimEnd('/') &&
            before[stringPreferencesKey("romm_username")] == username
        check(freshInstall || sameQaAccount) {
            "QA setup requires fresh app data or the same isolated QA account before Activity launch"
        }
        val password = account.getString("password")
        fixture.delete()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            withTimeout(90_000) {
                val result = activity.romMRepository.connectWithPassword(url.toString().trimEnd('/'), username, password)
                assertTrue("Isolated QA sign-in must succeed", result is SignInResult.Connected)
                val user = activity.romMRepository.getCurrentUser()
                assertTrue("QA token requires me.read", user is RomMResult.Success)
                assertEquals(username, (user as RomMResult.Success<RomMUser>).data.username)
                val count = activity.romMRepository.getPlatformCount()
                assertTrue("QA token requires catalog platform read", count is RomMResult.Success)
                check((count as RomMResult.Success<Int>).data in 1..4) { "Unexpected QA catalog size" }
                assertTrue(activity.romMRepository.syncPlatformsOnly().isSuccess)
                val platforms = activity.platformRepository.getAllPlatforms().filter { it.slug == "n64" }
                assertEquals("Isolated catalog needs its N64 platform", 1, platforms.size)
                for (platform in platforms) {
                    var sync = activity.romMRepository.syncPlatform(platform.id)
                    while (sync.alreadyInProgress) { delay(250); sync = activity.romMRepository.syncPlatform(platform.id) }
                    assertTrue("Isolated catalog sync must succeed", sync.errors.isEmpty())
                }
                if (args.getString("gameioQaCompleteSetup") != "false") activity.preferencesRepository.setFirstRunComplete()
                val game = activity.gameRepository.getByIgdbId(1074)
                assertNotNull("QA game must be stored for detail navigation", game)
                File(context.filesDir, "gameio-comments-qa-result.json").writeText(JSONObject().put("username", username).put("gameId", game!!.id).put("igdbId", 1074).toString())
            }
        }
    }
}
