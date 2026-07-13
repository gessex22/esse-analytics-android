plugins {
    alias(libs.plugins.essenalytics.android.library)
    alias(libs.plugins.essenalytics.android.hilt)
}

android {
    namespace = "com.esseanalytics.android.core.media"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    // NO hay dependencia de FFmpeg acá todavía — a propósito. MediaProber
    // (AndroidMediaProber.kt) ya tiene implementación real, sin ffmpeg: usa
    // android.media.MediaMetadataRetriever, parte del SDK. Los otros 3
    // (ThumbnailGenerator/TrimProcessor/NormalizeProcessor, ver el KDoc de
    // VideoProcessors.kt) SÍ necesitan ffmpeg de verdad para el filtro/los
    // flags exactos que ya corren en desktop, y siguen bloqueados: FFmpegKit
    // (arthenica) está retirado desde abril 2025, binarios sacados de Maven
    // Central, repo archivado, sin sucesor claro. Hay que decidir entre un
    // fork mantenido, compilar los binarios de ffmpeg propios via NDK, u otra
    // librería, ANTES de escribir esas 3 implementaciones.
}
