plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies { 
    implementation(project(":sdk:runtime-sdk")) 
    implementation(project(":runtime:cognition"))
    implementation(project(":runtime:services"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":libraries:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
