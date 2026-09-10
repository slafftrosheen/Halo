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
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
