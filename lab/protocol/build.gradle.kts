plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":lab:capture"))
    implementation(project(":lab:analysis"))
}
