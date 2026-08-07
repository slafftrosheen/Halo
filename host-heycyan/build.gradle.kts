plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":drivers:heycyan"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
