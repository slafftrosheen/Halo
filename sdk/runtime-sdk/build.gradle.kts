plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":libraries:common"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(libs.okhttp)
    api(project(":sdk:host-sdk"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
