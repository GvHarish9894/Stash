plugins {
    id("stash.kmp.library")
}

kotlin {
    // core:data runs its tests on the JVM.
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.androidx.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
