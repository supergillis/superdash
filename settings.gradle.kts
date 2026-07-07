pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "superdash"
include(":packages:app")
include(":packages:core")
include(":packages:ha-client")
include(":packages:esphome-server")
include(":packages:doorbell")
include(":packages:camera")
include(":packages:immich-client")
include(":packages:kiosk-bus")
include(":packages:screensaver")
include(":packages:voice")
