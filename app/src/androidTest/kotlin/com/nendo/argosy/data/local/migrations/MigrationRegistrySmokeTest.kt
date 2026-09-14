package com.nendo.argosy.data.local.migrations

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.DATABASE_VERSION
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

private const val TEST_DB = "alauncher-migration-test.db"
private const val FIRST_VALIDATED_VERSION = 6
private val NON_CANONICAL_CHECKPOINTS = setOf(60, 61, 86)

@RunWith(AndroidJUnit4::class)
class MigrationRegistrySmokeTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java,
    )

    @Test
    fun registry_is_contiguous_and_covers_current_version() {
        MigrationRegistry.assertContiguous(DATABASE_VERSION)
    }

    @Test
    fun registry_contains_one_migration_per_step() {
        val expected = DATABASE_VERSION - 1
        check(MigrationRegistry.ALL.size == expected) {
            "Expected $expected migrations, found ${MigrationRegistry.ALL.size}"
        }
    }

    @Test
    fun migrate_all_versions_from_first_schema_to_current() {
        helper.createDatabase(TEST_DB, FIRST_VALIDATED_VERSION).close()

        val exportedVersions = InstrumentationRegistry.getInstrumentation().context.assets
            .list(ALauncherDatabase::class.java.name)
            .orEmpty()
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .toSet()
        check(FIRST_VALIDATED_VERSION in exportedVersions && DATABASE_VERSION in exportedVersions)

        val migrations = MigrationRegistry.ALL
            .filter { it.startVersion >= FIRST_VALIDATED_VERSION }
            .sortedBy { it.startVersion }

        val pending = mutableListOf<Migration>()
        migrations.forEach { migration ->
            pending += migration
            // v60/v61 exports omit indices already created by 59→60 (present in v62).
            // v86 was exported after a same-version table addition later moved to 86→87.
            // v155/v157 were never exported. Validate the next historical checkpoint
            // with every intervening migration, without inventing historical schemas.
            if (migration.endVersion !in exportedVersions || migration.endVersion in NON_CANONICAL_CHECKPOINTS) {
                return@forEach
            }
            helper.runMigrationsAndValidate(
                TEST_DB,
                migration.endVersion,
                true,
                *pending.toTypedArray(),
            ).close()
            pending.clear()
        }
        check(pending.isEmpty())
    }

    @Test
    fun historical_core_options_paths_preserve_data_to_current() {
        for (startVersion in listOf(85, 86)) {
            helper.createDatabase(TEST_DB, startVersion).apply {
                execSQL(
                    "INSERT INTO play_sessions " +
                        "(id, gameId, gameTitle, platformSlug, startTime, endTime, continued, " +
                        "deviceId, deviceManufacturer, deviceModel" +
                        if (startVersion == 86) {
                            ", activePlayMs, standbyMs) VALUES " +
                                "(1, 1, 'Migration fixture', 'nes', 100, 200, 0, 'test', 'test', 'test', 70, 30)"
                        } else {
                            ") VALUES (1, 1, 'Migration fixture', 'nes', 100, 200, 0, 'test', 'test', 'test')"
                        }
                )
                if (startVersion == 86) {
                    execSQL("INSERT INTO core_option_overrides VALUES ('test-core', 'test-option', 'preserved')")
                }
                close()
            }
            val migrations = MigrationRegistry.ALL.filter {
                it.startVersion >= startVersion && it.endVersion <= 87
            }
            helper.runMigrationsAndValidate(TEST_DB, 87, true, *migrations.toTypedArray()).apply {
                query("SELECT gameTitle, activePlayMs, standbyMs FROM play_sessions WHERE id = 1").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Migration fixture", it.getString(0))
                    assertEquals(if (startVersion == 86) 70L else 0L, it.getLong(1))
                    assertEquals(if (startVersion == 86) 30L else 0L, it.getLong(2))
                }
                if (startVersion == 85) {
                    execSQL("INSERT INTO core_option_overrides VALUES ('test-core', 'test-option', 'preserved')")
                }
                close()
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, ALauncherDatabase::class.java, TEST_DB)
                .addMigrations(*MigrationRegistry.ARRAY)
                .build()
            try {
                database.openHelper.writableDatabase.query(
                    "SELECT value FROM core_option_overrides WHERE coreId = 'test-core'"
                ).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("preserved", it.getString(0))
                }
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun room_can_open_after_full_migration_chain() {
        helper.createDatabase(TEST_DB, FIRST_VALIDATED_VERSION).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(context, ALauncherDatabase::class.java, TEST_DB)
            .addMigrations(*MigrationRegistry.ARRAY)
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
    }
}
