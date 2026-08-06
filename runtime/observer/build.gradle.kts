plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.runtime.observer"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:cognition"))
}
