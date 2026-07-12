// Root build file — declara los plugins una vez (apply false) para que cada
// módulo los aplique sin repetir versión. La lógica compartida real vive en
// build-logic/convention (convention plugins), no acá.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
