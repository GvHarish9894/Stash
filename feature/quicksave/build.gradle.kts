plugins {
    id("stash.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.navigation.compose)
        }
    }
}
