plugins {
    id("com.android.library")
    }

android {
    namespace = "com.halo.engines.diagnostics"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":runtime:services"))
    implementation(project(":libraries:common"))
}


