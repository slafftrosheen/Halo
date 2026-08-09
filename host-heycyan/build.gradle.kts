plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.host.heycyan"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":drivers:heycyan"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
