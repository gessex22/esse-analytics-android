package com.esseanalytics.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget
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
    //
    // transitionFactory propio en vez de .crossfade(200) a secas: Coil ya
    // salta el fundido por default cuando el resultado viene de
    // DataSource.MEMORY_CACHE, pero NO cuando viene de DISK -- y la caché de
    // memoria de acá arriba es chica (25% de la memoria de la app), así que
    // se vacía fácil entre visitas. Reportado en dispositivo real: al volver
    // a una pestaña ya vista (Dashboard/Calendario/Estadísticas), las
    // miniaturas -- que ya estaban en disco, no en la red -- volvían a
    // fundir 200ms cada vez, y con varias miniaturas por pantalla
    // completando en momentos distintos, se veía como "los elementos tardan
    // en aparecer" durante casi un segundo. Con esto, memoria Y disco se
    // muestran directo; el fundido de 200ms queda solo para una carga real
    // (primera vez, desde red).
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .transitionFactory(SkipCachedCrossfadeTransitionFactory(durationMillis = 200))
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

private class SkipCachedCrossfadeTransitionFactory(
    private val durationMillis: Int,
) : Transition.Factory {
    override fun create(target: TransitionTarget, result: ImageResult): Transition {
        val isAlreadyOnDevice = result is SuccessResult &&
            (result.dataSource == DataSource.MEMORY_CACHE || result.dataSource == DataSource.DISK)
        return if (isAlreadyOnDevice) {
            Transition.Factory.NONE.create(target, result)
        } else {
            CrossfadeTransition(target, result, durationMillis)
        }
    }
}
