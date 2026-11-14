plugins {
    id("stash.kmp.library")
}

// Fakes for core:platform capabilities live here. The JVM target serves pure modules' tests (core:domain jvmTest);
// Android and iOS serve library and feature tests.
kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:platform"))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            // Feature tests read paged lists with asSnapshot.
            api(libs.androidx.paging.testing)
        }
    }
}
