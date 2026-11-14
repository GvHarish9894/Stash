plugins {
    id("stash.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.paging.compose)
            implementation(libs.navigation.compose)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
