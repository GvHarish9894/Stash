import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Dependency rule 2: a feature sees only domain, designsystem and resources.
plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Compose UI tests need a window: `src/composeUiTest/kotlin` runs on the iOS simulator (part of `check`) and as an
// Android device test (`connectedAndroidDeviceTest`), never as an Android host test.
val composeUiTestDir = "src/composeUiTest/kotlin"

extensions.configure<KotlinMultiplatformExtension> {
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":core:domain"))
        implementation(project(":core:designsystem"))
        implementation(project(":core:resources"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("compose-ui-test").get())
        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
    }
    sourceSets.matching { it.name == "iosTest" }.configureEach { kotlin.srcDir(composeUiTestDir) }
    sourceSets.matching { it.name == "androidDeviceTest" }.configureEach {
        kotlin.srcDir(composeUiTestDir)
        dependencies {
            implementation(libs.findLibrary("androidx-testRunner").get())
            // Declares the ComponentActivity runComposeUiTest launches; its version is aligned by the androidx.compose.ui
            // group constraints that compose-ui-test brings in.
            implementation("androidx.compose.ui:ui-test-manifest")
        }
    }
}
