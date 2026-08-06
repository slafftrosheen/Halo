plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.engines.reasoning"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":runtime:services"))
    implementation(project(":runtime:cognition"))
    implementation(project(":libraries:halo-cognition-language"))
    api(libs.kotlinx.datetime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
}
