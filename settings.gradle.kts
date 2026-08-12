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
  }
}

rootProject.name = "Halo"

include(":app")

include(":runtime:kernel")
include(":runtime:events")
include(":runtime:commands")
include(":runtime:cognition")
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
include(":host-android")
include(":engines:memory")
include(":engines:workflow")
include(":engines:provider")
include(":engines:device")
include(":engines:context")
include(":engines:privacy")
include(":engines:automation")
include(":engines:notification")
include(":engines:diagnostics")

include(":drivers:heycyan")
include(":drivers:simulator")
include(":providers:llamacpp")
include(":providers:android-stt")
include(":providers:android-tts")
include(":providers:android-assistant")

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
