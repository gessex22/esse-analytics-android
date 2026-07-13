package com.esseanalytics.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Configuration.Provider + HiltWorkerFactory: sin esto, los @HiltWorker de
// feature:upload (UploadWorker) no podrían recibir sus dependencias vía
// constructor -- WorkManager los instanciaría con reflection pelada. El
// AndroidManifest desactiva el auto-init default de WorkManager (androidx
// startup) para que use ESTA config en vez de la que trae por defecto.
@HiltAndroidApp
class EsseAnalyticsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
