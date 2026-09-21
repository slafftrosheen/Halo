plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.sherpa.stt"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    api("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:host-sdk"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
