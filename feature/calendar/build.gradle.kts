plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.calendar"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.coil.compose)
    // CalendarViewModel inyecta @CentralRetrofit Retrofit directo (para
    // retrofit.baseUrl(), armar la URL de miniatura de Nube) -- core:network
    // lo declara `implementation`, no es transitivo. Mismo motivo que
    // feature:library/feature:stats/feature:upload/feature:remotelibrary.
    // Sin esto, KSP no puede resolver el tipo Retrofit al generar el código
    // de Hilt para CalendarViewModel ("Retrofit could not be resolved"),
    // rompiendo compileDebugKotlin de TODA la app (Gradle no sigue después
    // de un módulo fallido) -- bug preexistente nunca detectado porque Lint
    // corría antes que Assemble debug en el CI y lo tapaba (ver Fase 6).
    implementation(libs.retrofit.core)
}
