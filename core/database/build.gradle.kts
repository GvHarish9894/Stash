import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    id("stash.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Room's Android builder needs a Context, so the in-memory tests run on a JVM host target instead.
    jvm()
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonAndroid") {
                withJvm()
                group("ios")
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.androidx.room.runtime)
            api(libs.androidx.room.paging)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("nonAndroidTest").dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.room.testing)
            // room-testing pulls json-okio 1.8.1, which does not link against json 1.13.0 on Kotlin/Native.
            implementation(project.dependencies.platform(libs.kotlinx.serialization.bom))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas; simctl passes SIMCTL_CHILD_* variables to the test process.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_ROOM_SCHEMA_DIR", "$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
