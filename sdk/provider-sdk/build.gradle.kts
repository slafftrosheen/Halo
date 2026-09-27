plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.moshi.kotlin)
    implementation(project(":libraries:common"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
