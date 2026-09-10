plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":sdk:provider-sdk"))
}
