plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.libraries.testing"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":libraries:common"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:driver-sdk"))
    implementation(project(":sdk:provider-sdk"))
}
