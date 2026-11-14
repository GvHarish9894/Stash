import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("stash.kmp.library")
}

// Dependency rule 3: the only module that sees database, network, preferences and platform.
kotlin {
    // Room's Android builder needs a Context, so the in-memory tests run on a JVM host target and the iOS simulator.
    jvm()
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withJvm()
                group("ios")
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:platform"))
            implementation(project(":core:database"))
            implementation(project(":core:network"))
            implementation(project(":core:preferences"))
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        getByName("nonAndroidTest").dependencies {
            implementation(project(":core:testing"))
            implementation(libs.ktor.client.mock)
            implementation(libs.androidx.paging.testing)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
