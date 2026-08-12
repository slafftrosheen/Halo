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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation(project(":sdk:provider-sdk"))
}
