plugins {
    id("com.android.library")
}
android { namespace = "com.halo.engines.planning"; compileSdk = 34; defaultConfig { minSdk = 26 } }
dependencies { 
    implementation(project(":sdk:runtime-sdk")) 
    implementation(project(":runtime:cognition"))
    implementation(project(":runtime:services"))
    implementation(project(":libraries:halo-cognition-language"))
    implementation(project(":libraries:common"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
