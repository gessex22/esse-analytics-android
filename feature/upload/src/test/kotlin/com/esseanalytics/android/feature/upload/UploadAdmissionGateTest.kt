package com.esseanalytics.android.feature.upload

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadWaitingStageTest {
    @Test
    fun runningWorkerWaitingForSlotRemainsPendingAndCancelable() {
        val waitingProgress = Data.Builder()
            .putBoolean(UploadWorker.KEY_WAITING_FOR_UPLOAD_SLOT, true)
            .build()
        val admittedProgress = Data.EMPTY

        assertEquals(
            PlatformPublishStage.PENDING,
            runningPlatformStage(waitingProgress.getBoolean(UploadWorker.KEY_WAITING_FOR_UPLOAD_SLOT, false)),
        )
        assertEquals(
            PlatformPublishStage.UPLOADING,
            runningPlatformStage(admittedProgress.getBoolean(UploadWorker.KEY_WAITING_FOR_UPLOAD_SLOT, false)),
        )
    }
}
