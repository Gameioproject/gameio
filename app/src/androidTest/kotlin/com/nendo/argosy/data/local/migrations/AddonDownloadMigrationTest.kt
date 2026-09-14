package com.nendo.argosy.data.local.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonDownloadMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ALauncherDatabase::class.java)

    @Test fun pendingDownloadRetainsIdentityProgressAndOwner() {
        val name = "addon-download-migration-test.db"
        helper.createDatabase(name, 180).apply {
            execSQL("""INSERT INTO download_queue
                (id,gameId,rommId,fileName,gameTitle,platformSlug,bytesDownloaded,totalBytes,state,createdAt,isMultiFileRom,ownerUserId)
                VALUES (7,12,44000019,'Game.zip','Game','snes',123,1000,'PAUSED',1,0,9)""")
            close()
        }
        helper.runMigrationsAndValidate(name, 181, true, Migration_180_181).use { db ->
            db.query("SELECT gameId,rommId,bytesDownloaded,state,ownerUserId,addonSourceJson FROM download_queue WHERE id=7").use {
                assertTrue(it.moveToFirst())
                assertEquals(12L, it.getLong(0))
                assertEquals(44000019L, it.getLong(1))
                assertEquals(123L, it.getLong(2))
                assertEquals("PAUSED", it.getString(3))
                assertEquals(9L, it.getLong(4))
                assertTrue(it.isNull(5))
            }
            val source = "{\"addonId\":\"test\",\"catalogKey\":\"123:snes\"}"
            db.execSQL("UPDATE download_queue SET addonSourceJson=? WHERE id=7", arrayOf(source))
            db.query("SELECT addonSourceJson FROM download_queue WHERE id=7").use {
                assertTrue(it.moveToFirst())
                assertEquals(source, it.getString(0))
            }
        }
    }
}
