plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.halo.providers.sherpa.stt"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:host-sdk"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
