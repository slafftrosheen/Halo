plugins {
    id("com.android.library")
}

android {
    namespace = "com.halo.providers.litert"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":libraries:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.litertlm.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.mockwebserver)
}

// LiteRT-LM 0.17.1 SDK classes use Java 21 bytecode. Android compilation stays at 17;
// host boundary tests need a JVM capable of loading the real SDK contract.
val sdkTestToolchains = extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
tasks.withType<Test>().configureEach {
    javaLauncher.set(sdkTestToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
