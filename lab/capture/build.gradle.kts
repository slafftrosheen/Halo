plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // NOTE: BLE capture types (BleCapture, CaptureSessionStore, etc.) currently
    // live in :drivers:heycyan:codec. They will be migrated here when a second
    // driver needs them. The dependency below is intentionally absent until
    // that migration happens to avoid a JVM-module -> Android-library link.
}
