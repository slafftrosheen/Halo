plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.runtime.speech"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":engines:speech")) // Need SpeechSegment and AudioPerceptionEngine references for now
    implementation(project(":engines:agents"))
}
