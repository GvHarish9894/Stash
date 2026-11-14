plugins {
    id("stash.kmp.pure")
    alias(libs.plugins.kover)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
            // Decision D3: PagingData appears in ItemRepository, as the architecture plan specifies.
            api(libs.androidx.paging.common)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Line coverage of the JVM tests must stay at or above 80%: ./gradlew :core:domain:koverVerify
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
