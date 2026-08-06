plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.engines.agents"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:services"))
    implementation(project(":runtime:cognition"))
    implementation(project(":engines:reasoning"))
    implementation(project(":engines:interpretation"))
    implementation(project(":engines:planning"))
    implementation(project(":engines:execution"))
    implementation(project(":engines:memory"))
    implementation(project(":engines:speech"))
    api(libs.kotlinx.datetime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
