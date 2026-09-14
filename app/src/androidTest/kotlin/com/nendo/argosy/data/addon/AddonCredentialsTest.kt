package com.nendo.argosy.data.addon

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AddonCredentialsTest {
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    private lateinit var alias: String

    @Before fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "addon-credentials-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        alias = "gameio.test.addons.${UUID.randomUUID()}"
    }

    @After fun cleanUp() {
        directory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test fun encryptedCredentialsAndSelectedTorrentSurviveNewRepository() = runBlocking {
        val credentials = AddonCredentials(context, alias)
        val testToken = "test-account-token-never-sent-to-a-service"
        assertNull(credentials.token())
        credentials.save(testToken)
        credentials.rememberTorrent("a".repeat(64), "selected-torrent")
        assertTrue(credentials.hasAccount.value)
        val stored = File(directory, "addon-realdebrid.bin").readBytes()
        assertFalse(stored.toString(Charsets.UTF_8).contains(testToken))
        assertFalse(stored.toString(Charsets.UTF_8).contains("selected-torrent"))

        val reopened = AddonCredentials(context, alias)
        assertEquals(testToken, reopened.token())
        assertEquals("selected-torrent", reopened.torrentId("a".repeat(64)))
        assertTrue(reopened.hasAccount.value)
        reopened.clear()
        assertFalse(reopened.hasAccount.value)
        assertNull(AddonCredentials(context, alias).token())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun replacingAccountDoesNotReuseAnotherAccountsTorrent() = runBlocking {
        val credentials = AddonCredentials(context, alias)
        credentials.save("first-test-account")
        credentials.rememberTorrent("a".repeat(64), "old-torrent")
        credentials.save("second-test-account")
        assertEquals("second-test-account", AddonCredentials(context, alias).token())
        assertNull(credentials.torrentId("a".repeat(64)))
    }
}
