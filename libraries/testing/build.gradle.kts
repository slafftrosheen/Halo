plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":libraries:common"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:driver-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:host-sdk"))
}
