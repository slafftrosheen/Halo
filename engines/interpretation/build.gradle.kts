plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":runtime:cognition"))
    implementation(project(":runtime:services"))
    implementation(project(":engines:speech"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":runtime:kernel"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
