plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies { 
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":runtime:provider-core"))
    api(project(":libraries:halo-cognition-language"))
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
