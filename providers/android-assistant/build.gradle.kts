plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.android.assistant"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation(project(":sdk:provider-sdk"))
}
