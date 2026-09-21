plugins {
    alias(libs.plugins.android.test)
    // alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.halo.baselineprofile"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

// baselineProfile {
//     useConnectedDevices = true
// }

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
}
