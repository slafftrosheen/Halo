plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:cognition"))
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
}
