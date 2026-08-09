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
    implementation(libs.moshi.kotlin)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":sdk:driver-sdk"))
    implementation(project(":libraries:logging"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
