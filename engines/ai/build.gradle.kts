plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":engines:provider"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

