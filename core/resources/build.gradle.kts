plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.components.resources)
        }
    }
}

// Other modules read strings and fonts through this module's Res class.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.techgv.stash.core.resources"
}
