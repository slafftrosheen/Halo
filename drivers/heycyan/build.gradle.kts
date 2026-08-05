plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.drivers.heycyan"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":sdk:driver-sdk"))
}
