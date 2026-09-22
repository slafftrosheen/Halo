plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.llamacpp"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":sdk:provider-sdk"))
    
    // LlamaCpp Native
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
    
    // HTTP & JSON for Ollama Provider
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)

    testImplementation(libs.junit)
}
