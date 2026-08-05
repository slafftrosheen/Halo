plugins {
    id("com.android.library")
    
}

android {
    namespace = "com.halo.plugin.calendar"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":halo-core"))
}
