plugins {
    id("com.android.library")
}
android { 
    namespace = "com.halo.runtime.cognition"; 
    compileSdk = 34; 
    defaultConfig { minSdk = 26 } 
}
dependencies { 
    implementation(project(":sdk:runtime-sdk"))
    api(project(":libraries:halo-cognition-language"))
    api(libs.kotlinx.datetime)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}