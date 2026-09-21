plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":runtime:plugins"))
    implementation(project(":runtime:services"))
    implementation(project(":libraries:common"))
}
