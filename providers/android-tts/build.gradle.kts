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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:host-sdk"))
}
