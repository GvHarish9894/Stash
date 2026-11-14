plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    android {
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":core:platform"))
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.glance.appwidget)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:resources"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:platform"))
            implementation(project(":core:network"))
            listOf("shelf", "search", "archive", "reader", "settings", "streak", "quicksave", "widgetpreview")
                .forEach { implementation(project(":feature:$it")) }
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        iosMain.dependencies {
            implementation(project(":core:platform"))
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
            implementation(libs.compose.ui.test)
            // The in-app metadata fetch test and the journey tests answer the fetchers from a mock engine.
            implementation(project(":core:network"))
            implementation(libs.ktor.client.mock)
        }
        // The journey tests need a window: they run on the iOS simulator (part of `check`) and as Android device tests.
        iosTest { kotlin.srcDir("src/composeUiTest/kotlin") }
        getByName("androidDeviceTest").kotlin.srcDir("src/composeUiTest/kotlin")
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.work.testing)
            implementation(libs.glance.appwidget.testing)
            // The ComponentActivity runComposeUiTest launches; aligned by compose-ui-test's constraints.
            implementation("androidx.compose.ui:ui-test-manifest")
        }
    }
}

// The JSON fixtures the Swift tests also use (widget.json, inbox-entry.json); simctl hands SIMCTL_CHILD_* variables to
// the test process.
val swiftFixtures = rootProject.file("iosApp/StashTests/Fixtures")
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    inputs.dir(swiftFixtures)
    environment("SIMCTL_CHILD_STASH_FIXTURES", swiftFixtures.absolutePath)
}
