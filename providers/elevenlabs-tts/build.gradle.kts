plugins { id("com.android.library") }
android {
    namespace = "com.halo.providers.elevenlabs.tts"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}
dependencies {
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
