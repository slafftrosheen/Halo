plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.host.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":engines:speech"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:cognition"))
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation(project(":runtime:debug"))
}

