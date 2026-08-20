plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(project(":libraries:common"))
    implementation(project(":libraries:halo-cognition-language"))
    api(project(":sdk:host-sdk"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

