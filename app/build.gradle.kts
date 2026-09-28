plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.halo.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.aistudio.braintool.xkqzps"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "1.0-RC1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    val customDebugKeystore = file("${rootDir}/debug.keystore")
    if (customDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = customDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugConfig = signingConfigs.findByName("debugConfig")
      if (debugConfig != null) {
        signingConfig = debugConfig
      }
    }
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.findByName("debugConfig")
        ?: signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      isDebuggable = false
      isProfileable = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}


// pluginManager.withPlugin("com.android.application") {
//   pluginManager.apply("androidx.baselineprofile")
// }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  // implementation(project(":halo-xxx"))
  // implementation(project(":halo-xxx"))
  // implementation(project(":halo-xxx"))
  // implementation(project(":halo-xxx"))
  // implementation(project(":halo-xxx"))
  // implementation(project(":halo-xxx"))
  
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.appsearch)
  implementation(libs.androidx.appsearch.local.storage)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(project(":providers:litert"))
  testImplementation(project(":providers:ollama"))
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  // Hermetic instrumentation layer: runs on an emulator with no hardware, network or credentials.
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.uiautomator)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "baselineProfile"(project(":baselineprofile"))
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.androidx.appsearch.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
dependencies {
    implementation(project(":engines:ai"))
    implementation(project(":host-android"))
    implementation(project(":sdk:driver-sdk"))
    implementation(project(":sdk:host-sdk"))
    implementation(project(":sdk:provider-sdk"))
    implementation(project(":sdk:runtime-sdk"))
    implementation(project(":libraries:common"))
    implementation(project(":libraries:halo-cognition-language"))
}
dependencies {
    implementation(project(":runtime:events"))
    implementation(project(":runtime:commands"))
    implementation(project(":runtime:queries"))
    implementation(project(":runtime:scheduler"))
    implementation(project(":runtime:registry"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:speech"))
    implementation(project(":runtime:observer"))
    implementation(project(":engines:execution"))
    implementation(project(":libraries:testing"))
    implementation(project(":libraries:logging"))
}
dependencies {
    implementation(project(":runtime:services"))
    implementation(project(":engines:memory"))
    implementation(project(":engines:diagnostics"))
    implementation(project(":engines:reasoning"))
    implementation(project(":engines:interpretation"))
    implementation(project(":engines:speech"))
    implementation(project(":host-android"))
    implementation(project(":runtime:intents"))
    implementation(project(":runtime:plugins"))
    implementation(project(":runtime:speech"))
}
dependencies {
    implementation(project(":engines:perception"))
    implementation(project(":engines:reasoning"))
    implementation(project(":engines:interpretation"))
    implementation(project(":engines:planning"))
    implementation(project(":engines:execution"))
    implementation(project(":engines:agents"))
}
dependencies {
    implementation(project(":runtime:cognition"))
    implementation(project(":providers:litert"))
    implementation(project(":providers:ollama"))
    implementation(project(":providers:sherpa-stt"))
    implementation(project(":providers:android-tts"))
    implementation(project(":providers:android-assistant"))
    implementation(project(":providers:openrouter"))
    implementation(project(":providers:openai"))
    implementation(project(":drivers:heycyan"))
    implementation(project(":drivers:simulator"))
}

tasks.register<Copy>("copyApkToReleases") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    into(rootProject.layout.projectDirectory.dir("releases"))
    include("app-debug.apk")
    rename("app-debug.apk", "halo-alpha-debug.apk")
}

afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy("copyApkToReleases")
    }
}
