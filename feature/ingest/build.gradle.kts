plugins {
    alias(libs.plugins.essenalytics.android.feature)
}

android {
    namespace = "com.esseanalytics.android.feature.ingest"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    implementation(project(":core:datastore"))
}
