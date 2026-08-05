plugins {
    id("com.android.library")
    
}

android {
    namespace = "com.halo.driver.heycyan"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":halo-core"))
}
