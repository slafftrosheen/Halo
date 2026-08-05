plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.engines.memory"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":runtime:services"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
