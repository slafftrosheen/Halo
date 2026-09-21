plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":runtime:cognition"))
    testImplementation("junit:junit:4.13.2")

}
