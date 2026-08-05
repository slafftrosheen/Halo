plugins {
    id("com.android.library")
    }

android {
    namespace = "com.halo.engines.ai"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":engines:provider"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}


