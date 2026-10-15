package com.esseanalytics.android.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PendingCatalogNameEntity
import com.esseanalytics.android.core.database.dao.CatalogNameAckResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CatalogNameOutboxInstrumentedTest {
    @Test fun editDuringInflightRequestIsReadAndAppliedAtomicallyWithAck() = runBlocking {
        val db = database()
        try {
            val dao = db.pendingCatalogNameDao()
            db.fileDao().insert(file())
            db.fileDao().updateCatalogName("client-a", "A", 0, "pending", "user-a", "https://central/")
            dao.enqueue(row("op-a", "A"))
            assertEquals(1, dao.markSending("op-a"))
            dao.enqueue(row("op-b", "B"))

            val result = dao.applyAcknowledgement("op-a", "A", 1, "client-a", "op-c")
            assertEquals(CatalogNameAckResult.SendDesired(1), result)
            val next = dao.findByOperationId("op-c")
            assertNotNull(next)
            assertEquals("B", next?.displayName)
            assertEquals(1, next?.baseRevision)
            assertEquals("pending", next?.state)
            db.fileDao().findByClientFileId("client-a")!!.let {
                assertEquals(1, it.displayNameRevision)
                assertEquals("pending", it.displayNameState)
            }
        } finally { db.close() }
    }

    @Test fun replayAlreadySupersededPreservesLatestProposalAsConflict() = runBlocking {
        val db = database()
        try {
            val dao = db.pendingCatalogNameDao()
            db.fileDao().insert(file())
            db.fileDao().updateCatalogName("client-a", "A", 0, "pending", "user-a", "https://central/")
            dao.enqueue(row("op-a", "A"))
            dao.markSending("op-a")
            dao.enqueue(row("op-b", "B"))

            val result = dao.applyAcknowledgement("op-a", "Remote", 2, "client-a", UUID.randomUUID().toString())
            assertEquals(CatalogNameAckResult.Conflict("Remote", 2), result)
            val retained = dao.findByOperationId("op-a")
            assertEquals("A", retained?.displayName)
            assertEquals("B", retained?.desiredName)
            assertEquals("conflict", retained?.state)
            db.fileDao().findByClientFileId("client-a")!!.let {
                assertEquals(2, it.displayNameRevision)
                assertEquals("conflict", it.displayNameState)
                assertEquals("A", it.displayName)
            }
        } finally { db.close() }
    }

    @Test fun hydrationKeepsPendingProposalAndIgnoresOlderRevisionWithinSameAccount() = runBlocking {
        val db = database()
        try {
            val dao = db.pendingCatalogNameDao()
            db.fileDao().insert(file())
            db.fileDao().updateCatalogName("client-a", "Mi propuesta", 0, "pending", "user-a", "https://central/")
            dao.enqueue(row("op-a", "Mi propuesta"))
            assertEquals(false, dao.hydrateIfNoPending("https://central/|user-a", "https://central/", "client-a", "Servidor", 2, "user-a"))
            assertEquals("Mi propuesta", db.fileDao().findByClientFileId("client-a")?.displayName)

            dao.clear("https://central/|user-a", "https://central/", "client-a")
            dao.hydrateIfNoPending("https://central/|user-a", "https://central/", "client-a", "Nuevo 1.2", 3, "user-a")
            dao.hydrateIfNoPending("https://central/|user-a", "https://central/", "client-a", "Viejo", 2, "user-a")
            val latest = db.fileDao().findByClientFileId("client-a")!!
            assertEquals("Nuevo 1.2", latest.displayName)
            assertEquals(3, latest.displayNameRevision)
            assertEquals("VID_20261014.mp4", latest.fileName)
            assertEquals("content://media/a", latest.filePath)

            dao.hydrateIfNoPending("https://central/|user-b", "https://central/", "client-a", "Otra cuenta", 1, "user-b")
            assertEquals("user-b", db.fileDao().findByClientFileId("client-a")?.displayNameOwnerId)
            assertEquals(1, db.fileDao().findByClientFileId("client-a")?.displayNameRevision)
        } finally { db.close() }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, EsseAnalyticsDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun file() = FileEntity(
        clientFileId = "client-a", fileName = "VID_20261014.mp4", filePath = "content://media/a",
        status = "TRANSCRITO", contentStatus = "BORRADOR", createdAtEpochMs = 1, updatedAtEpochMs = 2,
        displayNameOwnerId = "user-a", displayNameServerUrl = "https://central/",
    )

    private fun row(op: String, name: String) = PendingCatalogNameEntity(
        operationId = op, userKey = "https://central/|user-a", serverUrl = "https://central/",
        clientFileId = "client-a", contentId = "content-a", displayName = name, baseRevision = 0,
    )
}
