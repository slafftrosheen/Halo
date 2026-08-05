plugins {
    id("com.android.library")
}
android { namespace = "com.halo.engines.perception"; compileSdk = 34; defaultConfig { minSdk = 26 } }
dependencies { implementation(project(":sdk:runtime-sdk")) }
