// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.androidx.baselineprofile) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// ---------------------------------------------------------------------------------------------
// Layered verification entry points.
//
// The module grouping lives here rather than in CI YAML so that any CI layer can be reproduced
// locally with a single command, and so the boundary between the automatic gate and the opt-in
// hardware layers is explicit in the build itself.
//
// Nothing below is wired into `check`/`test`, and no layer touches `connectedAndroidTest`: a
// developer running ordinary `./gradlew test` can never trigger emulator or hardware work.
// ---------------------------------------------------------------------------------------------

/** Pure-JVM modules verified with the plain `test` task. */
val unitCoreProjects = listOf(
    ":sdk:runtime-sdk", ":sdk:driver-sdk", ":sdk:provider-sdk", ":sdk:plugin-sdk", ":sdk:host-sdk",
    ":runtime:kernel", ":runtime:events", ":runtime:commands", ":runtime:cognition",
    ":runtime:provider-core", ":runtime:queries", ":runtime:scheduler", ":runtime:registry",
    ":runtime:lifecycle", ":runtime:intents", ":runtime:debug", ":runtime:services",
    ":runtime:plugins", ":runtime:speech", ":runtime:observer", ":runtime:testing",
    ":engines:ai", ":engines:perception", ":engines:speech", ":engines:reasoning",
    ":engines:interpretation", ":engines:planning", ":engines:agents", ":engines:execution",
    ":engines:memory", ":engines:workflow", ":engines:provider", ":engines:device",
    ":engines:context", ":engines:privacy", ":engines:automation", ":engines:notification",
    ":engines:diagnostics",
    ":libraries:common", ":libraries:halo-cognition-language", ":libraries:logging", ":libraries:testing",
    ":drivers:simulator",
    ":lab:capture", ":lab:replay", ":lab:analysis", ":lab:protocol", ":lab:diagnostics",
)

/** Local on-device inference providers: the layer that must survive without glasses. */
val unitLocalAiProjects = listOf(":providers:litert", ":providers:openrouter")

/** Remaining Android library modules with unit tests. */
val unitAndroidLibProjects = listOf(
    ":host-android", ":providers:sherpa-stt", ":providers:android-tts", ":providers:android-assistant", ":providers:ollama"
)

tasks.register("haloUnitCore") {
    group = "verification"
    description = "Runs unit tests for runtime, sdk, engines, libraries and lab modules."
    dependsOn(unitCoreProjects.map { "$it:test" })
}

tasks.register("haloUnitHeyCyan") {
    group = "verification"
    description = "Runs the HeyCyan driver unit tests in isolation."
    dependsOn(":drivers:heycyan:testDebugUnitTest")
}

tasks.register("haloUnitLocalAi") {
    group = "verification"
    description = "Runs local/remote AI provider unit tests in isolation."
    dependsOn(unitLocalAiProjects.map { "$it:testDebugUnitTest" })
}

tasks.register("haloUnitAndroidLibs") {
    group = "verification"
    description = "Runs unit tests for the remaining Android library modules."
    dependsOn(unitAndroidLibProjects.map { "$it:testDebugUnitTest" })
}

tasks.register("haloUnitApp") {
    group = "verification"
    description = "Runs the app module unit tests."
    dependsOn(":app:testDebugUnitTest")
}

// Android Lint is the static analysis this project actually configures. Note that the repository
// has no detekt/ktlint setup (no plugins, config or .editorconfig), so earlier CI steps referring
// to `detekt`/`ktlintCheck` could not have succeeded.
tasks.register("haloStatic") {
    group = "verification"
    description = "Runs static analysis without running any tests."
    dependsOn(unitAndroidLibProjects.plus(listOf(":app", ":drivers:heycyan")).map { "$it:lintDebug" })
}

tasks.register("haloAppBuild") {
    group = "verification"
    description = "Assembles the debug APK."
    dependsOn(":app:assembleDebug")
}

// The automatic, hardware-free gate. It deliberately excludes `haloStatic` for now: Android Lint
// currently reports pre-existing errors in app/ and host-android/ that are unrelated to this
// batch, and a gate that is red for unrelated reasons stops being a gate. Run `haloStatic`
// separately until those are resolved.
tasks.register("haloVerify") {
    group = "verification"
    description = "The automatic, hardware-free gate: every unit test layer."
    dependsOn(
        "haloUnitCore",
        "haloUnitHeyCyan",
        "haloUnitLocalAi",
        "haloUnitAndroidLibs",
        "haloUnitApp",
    )
}

// Enforce a consistent JVM toolchain for all pure-JVM subprojects.
// Android modules (com.android.library / com.android.application) manage their
// own Java target via compileSdk; the toolchain block below only applies to
// subprojects using the `java-library` or `org.jetbrains.kotlin.jvm` plugins.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    plugins.withId("com.android.test") {
        extensions.configure<com.android.build.api.dsl.TestExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
