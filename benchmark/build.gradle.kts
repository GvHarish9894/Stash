import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Macrobenchmarks and the baseline-profile generator for :androidApp. Run by hand on a device (docs/PERFORMANCE.md);
// not part of `check`.
plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineprofile)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.techgv.stash.benchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Baseline profiles are collected on API 28+ without root.
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        // Runs against the app's `benchmark` build type (release code, debug key).
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    // The baseline-profile plugin keeps only the variants it creates. Generation uses its nonMinifiedRelease; the
    // macrobenchmarks run against the app's own `benchmark` build type (it has the seed receiver). The plugin's copies
    // of `benchmark` target app variants that do not exist.
    beforeVariants { it.enable = it.buildType in setOf("benchmark", "nonMinifiedRelease", "benchmarkRelease") }
}
