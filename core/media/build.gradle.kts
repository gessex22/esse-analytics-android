plugins {
    alias(libs.plugins.essenalytics.android.library)
    // Sin plugin de Hilt todavía — no hay una implementación real para bindear
    // (ver más abajo). Se agrega junto con la implementación real en Fase 1.
}

android {
    namespace = "com.esseanalytics.android.core.media"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    // NO hay dependencia de FFmpeg acá todavía — a propósito. Ver el comentario
    // en FfmpegProcessors.kt: FFmpegKit (arthenica) está retirado desde abril
    // 2025, binarios sacados de Maven Central, repo archivado, sin sucesor
    // claro. Agregar la dependencia real es un prerequisito de Fase 1, no de
    // este scaffold — hay que decidir entre un fork mantenido, compilar los
    // binarios de ffmpeg propios via NDK, u otra librería, ANTES de escribir
    // la implementación real de estos processors.
}
