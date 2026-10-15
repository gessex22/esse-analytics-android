package com.esseanalytics.android.core.database.dao

import com.esseanalytics.android.core.database.entity.PendingCatalogNameEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogNameAckDecisionTest {
    private fun row(name: String, desired: String = name, base: Int = 4) = PendingCatalogNameEntity(
        operationId = "op-a", userKey = "server|user", serverUrl = "https://example.test/",
        clientFileId = "client-file", displayName = name, desiredName = desired, baseRevision = base,
    )

    @Test fun exactSuccessorCompletesOrQueuesTheLatestProposal() {
        assertEquals(CatalogNameAckResult.Complete(5), decideCatalogNameAck(row("A"), "A", 5))
        assertEquals(CatalogNameAckResult.SendDesired(5), decideCatalogNameAck(row("A", "C"), "A", 5))
    }

    @Test fun supersededOrRegressedReplayKeepsTheProposalInConflict() {
        assertEquals(CatalogNameAckResult.Conflict("B", 6), decideCatalogNameAck(row("A", "C"), "B", 6))
        assertEquals(CatalogNameAckResult.Conflict("A", 3), decideCatalogNameAck(row("B"), "A", 3))
    }
}
