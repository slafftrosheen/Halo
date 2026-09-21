plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:services"))
    implementation(project(":runtime:cognition"))
    implementation(project(":engines:reasoning"))
    implementation(project(":engines:interpretation"))
    implementation(project(":engines:planning"))
    implementation(project(":engines:execution"))
    implementation(project(":engines:memory"))
    implementation(project(":engines:speech"))
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
