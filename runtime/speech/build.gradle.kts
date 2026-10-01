plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":engines:speech")) // Need SpeechSegment and AudioPerceptionEngine references for now
    implementation(project(":engines:agents"))
    implementation(project(":sdk:provider-sdk"))
}
