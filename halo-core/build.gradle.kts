plugins {
    id("com.android.library")
}
android {
    namespace = "com.halo.core"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
