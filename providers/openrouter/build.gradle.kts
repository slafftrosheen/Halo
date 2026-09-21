plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.openrouter"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
