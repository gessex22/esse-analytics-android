package com.esseanalytics.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Configuration.Provider + HiltWorkerFactory: sin esto, los @HiltWorker de
// feature:upload (UploadWorker) no podrían recibir sus dependencias vía
// constructor -- WorkManager los instanciaría con reflection pelada. El
// AndroidManifest desactiva el auto-init default de WorkManager (androidx
// startup) para que use ESTA config en vez de la que trae por defecto.
@HiltAndroidApp
class EsseAnalyticsApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // Sin esto Coil arma un ImageLoader default la primera vez que hace falta
    // (sin tuning de tamaños ni fundido) -- las miniaturas de LibraryScreen
    // hoy "aparecen de golpe" en vez de con la transición suave que el resto
    // de la app usa (motion/react en la web). crossfade(200) iguala esa
    // sensación; memoryCache/diskCache evitan redecodificar el mismo bitmap
    // al scrollear la lista para arriba y para abajo.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(200)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizePercent(0.02)
                .build()
        }
        .build()
}
