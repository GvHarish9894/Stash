plugins {
    id("stash.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.coil.compose)
        }
        // The web view's page history takes system back before the reader closes.
        androidMain.dependencies {
            implementation(libs.compose.ui.backhandler)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
