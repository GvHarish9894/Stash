import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Apply after stash.kmp.library.
pluginManager.apply("org.jetbrains.compose")
pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

val libs = the<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    // Compose resources ship as Android resources/assets, which the KMP library target disables by default.
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
        androidResources { enable = true }
    }
    sourceSets.commonMain.dependencies {
        implementation(libs.findLibrary("compose-runtime").get())
    }
}
