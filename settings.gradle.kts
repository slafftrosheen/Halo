pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
plugins { 
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" 
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io") {
      content {
        includeGroupByRegex("com\\.github\\..*")
      }
    }
  }
}

rootProject.name = "Halo"

include(":app")
include(":benchmark")
include(":baselineprofile")

include(":runtime:kernel")
include(":runtime:events")
include(":runtime:commands")
include(":runtime:cognition")
include(":runtime:provider-core")
include(":runtime:queries")
include(":runtime:scheduler")
include(":runtime:registry")
include(":runtime:lifecycle")
include(":runtime:intents")
include(":runtime:debug")
include(":runtime:services")
include(":runtime:plugins")
include(":runtime:speech")
include(":runtime:observer")
include(":runtime:testing")

include(":sdk:runtime-sdk")
include(":sdk:driver-sdk")
include(":sdk:provider-sdk")
include(":sdk:plugin-sdk")
include(":sdk:host-sdk")

include(":engines:ai")
include(":engines:perception")
include(":engines:speech")
include(":engines:reasoning")
include(":engines:interpretation")
include(":engines:planning")
include(":engines:agents")
include(":engines:execution")
include(":engines:memory")
include(":engines:workflow")
include(":engines:provider")
include(":engines:device")
include(":engines:context")
include(":engines:privacy")
include(":engines:automation")
include(":engines:notification")
include(":engines:diagnostics")
include(":host-android")
include(":host-ink")

include(":drivers:heycyan")
include(":drivers:simulator")
include(":providers:litert")
include(":providers:ollama")
include(":providers:sherpa-stt")
include(":providers:android-tts")
include(":providers:elevenlabs-tts")
include(":providers:android-assistant")
include(":providers:openrouter")
include(":providers:openai")
include(":providers:anthropic")
include(":providers:gemini")

// RUNTIME-055 remote-provider fabric presets
include(":providers:remote-groq")
include(":providers:remote-cerebras")
include(":providers:remote-opencode-zen")
include(":providers:remote-xai")
include(":providers:remote-opencode-companion")
include(":providers:remote-custom-openai-compatible")
include(":providers:remote-gemini-oauth")

include(":libraries:common")
include(":libraries:halo-cognition-language")
include(":libraries:logging")
include(":libraries:testing")

// Protocol Laboratory — device-agnostic capture/replay/analysis tooling
include(":lab:capture")
include(":lab:replay")
include(":lab:analysis")
include(":lab:protocol")
include(":lab:diagnostics")

include(":wear")
