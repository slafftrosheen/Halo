plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":runtime:cognition"))
    api(project(":sdk:runtime-sdk"))
    api(project(":sdk:driver-sdk"))
    api(project(":sdk:provider-sdk"))
    api(project(":runtime:plugins"))
}
