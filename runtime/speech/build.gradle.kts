plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":engines:speech")) // Need SpeechSegment and AudioPerceptionEngine references for now
    implementation(project(":engines:agents"))
    implementation(project(":sdk:provider-sdk"))
}
