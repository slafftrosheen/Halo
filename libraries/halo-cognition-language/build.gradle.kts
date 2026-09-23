plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    }

dependencies {
    implementation(project(":libraries:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
