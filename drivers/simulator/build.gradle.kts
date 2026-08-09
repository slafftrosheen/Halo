plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":sdk:driver-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}