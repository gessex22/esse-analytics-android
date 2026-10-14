package com.esseanalytics.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class Migration7To8InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), EsseAnalyticsDatabase::class.java,
    )

    @Test fun sourceNameMigrationPreservesExistingVideosAndUris() {
        val name = "import-names-migration"
        helper.createDatabase(name, 7).apply {
            execSQL("""
                INSERT INTO files (id, clientFileId, fileName, filePath, status, contentStatus,
                    platforms, platformsDiscarded, createdAtEpochMs, updatedAtEpochMs)
                VALUES (1, 'stable-content-id', 'Mi título.mp4', 'content://media/video/42',
                    'PENDIENTE', 'BORRADOR', 'youtube', '', 1000, 2000)
            """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT clientFileId, fileName, filePath, platforms, importSourceName FROM files WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("stable-content-id", cursor.getString(0))
                assertEquals("Mi título.mp4", cursor.getString(1))
                assertEquals("content://media/video/42", cursor.getString(2))
                assertEquals("youtube", cursor.getString(3))
                assertNull(cursor.getString(4))
            }
        }
    }
}
