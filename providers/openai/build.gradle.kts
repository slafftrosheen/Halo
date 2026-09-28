plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.openai"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
