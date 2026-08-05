plugins {
    id("com.android.library")
    
}

android {
    namespace = "com.halo.engine.memory"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":halo-core"))
    implementation(project(":halo-runtime"))
}
