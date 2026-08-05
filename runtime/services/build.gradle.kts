plugins {
    id("com.android.library")
    }

android {
    namespace = "com.halo.runtime.services"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":runtime:cognition"))
    api(project(":sdk:runtime-sdk"))
    api(project(":sdk:driver-sdk"))
    api(project(":sdk:provider-sdk"))
    api(project(":runtime:plugins"))
}


