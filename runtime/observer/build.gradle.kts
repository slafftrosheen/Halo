plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation(project(":libraries:common"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:cognition"))
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    testImplementation(libs.junit)
}
