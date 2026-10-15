package com.esseanalytics.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Comprueba en SQLite real que la migración del catálogo no pierde filas legacy. */
class PhoneCatalogMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EsseAnalyticsDatabase::class.java)

    @Test
    fun migration8To12PreservesFilesAndBlocksAmbiguousLegacyHistory() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL("INSERT INTO files (clientFileId,fileName,filePath,status,contentStatus,platforms,platformsDiscarded,createdAtEpochMs,updatedAtEpochMs) VALUES ('client-a','same.mp4','content://media/a','TRANSCRITO','BORRADOR','','',11,12)")
            execSQL("INSERT INTO pending_history_events (platform,platformId,fileName,createdAtEpochMs,attempts) VALUES ('youtube','yt-a','same.mp4',13,2)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
        helper.closeWhenFinished(db)

        db.query("SELECT clientFileId,fileName,filePath,status,createdAtEpochMs,contentSha256 FROM files").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("client-a", cursor.getString(0))
            assertEquals("same.mp4", cursor.getString(1))
            assertEquals("content://media/a", cursor.getString(2))
            assertEquals("TRANSCRITO", cursor.getString(3))
            assertEquals(11L, cursor.getLong(4))
            assertNull(cursor.getString(5))
        }
        db.query("SELECT platformId,attempts,contentId,clientFileId,userKey,serverUrl,state FROM pending_history_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("yt-a", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
            assertNull(cursor.getString(4))
            assertNull(cursor.getString(5))
            assertEquals("blocked", cursor.getString(6))
        }
        db.query("SELECT userKey,serverUrl,clientFileId,sha256,state,attempts FROM pending_catalog_registrations").use { cursor ->
            assertEquals(0, cursor.count)
        }

        // Scope check mirrors the DAO query: a pending row for another login/server is never visible,
        // and a legacy NULL-scope event is never re-homed to the active account.
        db.execSQL("INSERT INTO pending_history_events (platform,platformId,createdAtEpochMs,attempts,userKey,serverUrl,state,nextAttemptAtEpochMs) VALUES ('youtube','a',14,0,'user-a','https://central/','pending',0)")
        db.execSQL("INSERT INTO pending_history_events (platform,platformId,createdAtEpochMs,attempts,userKey,serverUrl,state,nextAttemptAtEpochMs) VALUES ('youtube','b',15,0,'user-b','https://central/','pending',0)")
        db.execSQL("INSERT INTO pending_history_events (platform,platformId,createdAtEpochMs,attempts,userKey,serverUrl,state,nextAttemptAtEpochMs) VALUES ('youtube','c',16,0,'user-a','https://lab/','pending',0)")
        db.query("SELECT platformId FROM pending_history_events WHERE userKey='user-a' AND serverUrl='https://central/' AND state='pending' AND nextAttemptAtEpochMs<=9999999999999").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("a", cursor.getString(0))
        }
        db.execSQL("UPDATE pending_history_events SET state='sent' WHERE platformId='a' AND userKey='user-b' AND serverUrl='https://central/' AND state='pending'")
        db.query("SELECT state FROM pending_history_events WHERE platformId='a'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("pending", cursor.getString(0))
        }
    }

    private companion object { const val TEST_DB = "phone-catalog-migration-test.db" }
}
