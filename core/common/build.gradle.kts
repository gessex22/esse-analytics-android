plugins {
    alias(libs.plugins.essenalytics.android.library)
}

android {
    namespace = "com.esseanalytics.android.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
