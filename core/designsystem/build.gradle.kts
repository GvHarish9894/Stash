plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
}

kotlin {
    android {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:resources"))
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compose UI tests need a window: they run on the iOS simulator (in `check`) and as Android device tests,
        // not as Android host tests.
        iosTest {
            kotlin.srcDir("src/composeUiTest/kotlin")
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/composeUiTest/kotlin")
            dependencies {
                implementation(libs.androidx.testRunner)
            }
        }
    }
}
