plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.host.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    api(project(":sdk:driver-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":engines:speech"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:cognition"))
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation(project(":runtime:debug"))
}
