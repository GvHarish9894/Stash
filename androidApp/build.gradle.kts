import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineprofile)
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

    // Installs the baseline profile on devices without Play's cloud profiles (sideloads, benchmarks).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":benchmark"))

    // Instrumented tests of what needs the real app manifest (launcher aliases, share target, process start).
    // core:testing brings the domain and capability types the tests read through the app's Koin graph.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.testRunner)
    androidTestImplementation(libs.androidx.testCore)
    androidTestImplementation(libs.kotlin.testJunit)
    androidTestImplementation(libs.koin.core)
}

// The version lives in the iOS configuration file so both platforms read one place; scripts/next-version.sh writes it.
val appVersion = Properties().apply { rootProject.file("iosApp/Configuration/Config.xcconfig").inputStream().use(::load) }

// Signing values come from the environment (CI) or from ~/.stash-signing/signing.properties (the machine that holds
// the keystores). Neither is in the repository: without them debug builds use the machine's own debug key and
// release builds stay unsigned.
val localSigning = Properties().apply {
    val file = File(System.getProperty("user.home"), ".stash-signing/signing.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(name: String): String? = System.getenv(name) ?: localSigning.getProperty(name)

android {
    namespace = "com.techgv.stash"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.techgv.stash"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersion.getProperty("CURRENT_PROJECT_VERSION").trim().toInt()
        versionName = appVersion.getProperty("MARKETING_VERSION").trim()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The end-to-end drivers change real app state; they run only by hand (see EndToEndDriverTest).
        testInstrumentationRunnerArguments["notAnnotation"] = "com.techgv.stash.EndToEndDriver"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        for (name in listOf("debug", "release")) {
            val prefix = "STASH_${name.uppercase()}"
            val keystore = signingValue("${prefix}_KEYSTORE_FILE") ?: continue
            maybeCreate(name).apply {
                storeFile = file(keystore)
                storePassword = signingValue("${prefix}_KEYSTORE_PASSWORD")
                keyAlias = signingValue("${prefix}_KEY_ALIAS")
                keyPassword = signingValue("${prefix}_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release code and shrinking, signed with the debug key so it installs on test devices; for measurements.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
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

// After android {}: the benchmark build type's configurations exist only once it is created.
dependencies {
    // The measurement seeder writes through the app's database (decision D31; benchmark build type only).
    "benchmarkImplementation"(project(":core:database"))
    "benchmarkImplementation"(libs.koin.core)
}

baselineProfile {
    // One committed profile in src/main serves every variant, so builds need no device.
    mergeIntoMain = true
}