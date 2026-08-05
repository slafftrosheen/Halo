plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.engines.provider"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
