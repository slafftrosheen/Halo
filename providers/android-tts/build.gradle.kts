plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.android.tts"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:host-sdk"))
}
