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
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
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
include(":runtime:queries")
include(":runtime:scheduler")
include(":runtime:registry")
include(":runtime:lifecycle")

include(":sdk:runtime-sdk")
include(":sdk:driver-sdk")
include(":sdk:provider-sdk")
include(":sdk:plugin-sdk")

include(":engines:voice")
include(":engines:memory")
include(":engines:workflow")
include(":engines:provider")
include(":engines:device")
include(":engines:context")
include(":engines:privacy")
include(":engines:automation")
include(":engines:notification")

include(":drivers:heycyan")
include(":providers:llamacpp")

include(":libraries:common")
include(":libraries:logging")
include(":libraries:testing")
