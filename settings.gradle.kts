rootProject.name = "Stash"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:preferences")
include(":core:network")
include(":core:platform")
include(":core:testing")
include(":core:designsystem")
include(":core:resources")
include(":feature:shelf")
include(":feature:search")
include(":feature:archive")
include(":feature:reader")
include(":feature:settings")
include(":feature:streak")
include(":feature:quicksave")
include(":feature:widgetpreview")
include(":benchmark")
