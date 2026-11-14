plugins {
    id("stash.kmp.library")
}

kotlin {
    // Capability interfaces live in commonMain; the JVM target only lets pure modules' JVM tests use their fakes.
    jvm()
    android {
        // The reminder notification's small icon and channel name.
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            // The App Group files: the share inbox and widget.json.
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // Clipboard.readUrl validates with LinkCapturePolicy.
            implementation(project(":core:domain"))
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.browser)
        }
    }
}
