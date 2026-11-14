plugins {
    id("stash.kmp.library")
}

kotlin {
    // core:data runs its tests on a plain JVM target, and it depends on this module.
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:domain"))
            api(libs.ktor.client.core)
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksoup)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
