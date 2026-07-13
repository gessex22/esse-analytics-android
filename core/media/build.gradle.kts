plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
}

android {
    namespace = "com.esseanalytics.android.core.media"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    // NO hay dependencia de FFmpeg acá, y no la va a haber -- decisión tomada
    // con el usuario (no solo "qué fork" sino un problema de licencia real:
    // libx264, el codec que hubiera hecho falta para Normalize, es GPL, y
    // esta es una app comercial de código cerrado). MediaProber
    // (AndroidMediaProber.kt) y TrimProcessor (AndroidTrimProcessor.kt) ya
    // tienen implementación real 100% SDK (MediaMetadataRetriever /
    // MediaExtractor+MediaMuxer). ThumbnailGenerator (con blur de fondo) y
    // NormalizeProcessor siguen sin implementar -- ver el comentario al
    // final de VideoProcessors.kt para el motivo real de cada uno.
}
