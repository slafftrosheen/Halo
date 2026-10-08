plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.halo.wear"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.aistudio.braintool.xkqzps"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.0-RC1"
    }
    signingConfigs {
        val key = file("${rootDir}/debug.keystore")
        if (key.exists()) getByName("debug") { storeFile = key; storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" }
    }
}
dependencies { implementation("com.google.android.gms:play-services-wearable:20.0.1") }
