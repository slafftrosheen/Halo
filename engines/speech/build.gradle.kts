plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":runtime:services"))
    implementation(project(":runtime:kernel"))
    implementation(project(":libraries:halo-cognition-language"))
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
