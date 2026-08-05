# Building Halo

How to compile the project on a fresh machine.

## Requirements
- Android Studio / IntelliJ IDEA
- Java 17+
- Android SDK 36+

## Commands
\\\ash
# Build the debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
\\\
"@

Set-Content -Path "DEBUG.md" -Value @"
# Debugging Halo

Developer tools and diagnostics.

- **Developer Dashboard**: Accessible via the UI, shows real-time EngineHealth and EngineState.
- **Timeline**: View cognitive events, thoughts, and actions.
