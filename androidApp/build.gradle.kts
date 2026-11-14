import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.foundation)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Instrumented tests of what needs the real app manifest (launcher aliases, share target, process start).
    // core:testing brings the domain and capability types the tests read through the app's Koin graph.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.androidx.testCore)
    androidTestImplementation(libs.kotlin.testJunit)
    androidTestImplementation(libs.koin.core)
}

android {
    namespace = "com.techgv.stash"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.techgv.stash"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The end-to-end drivers change real app state; they run only by hand (see EndToEndDriverTest).
        testInstrumentationRunnerArguments["notAnnotation"] = "com.techgv.stash.EndToEndDriver"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}