import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

pluginManager.apply("org.jetbrains.kotlin.multiplatform")
pluginManager.apply("com.android.kotlin.multiplatform.library")

val libs = the<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
        // :core:database -> com.techgv.stash.core.database
        namespace = "com.techgv.stash" + project.path.replace(':', '.')
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // Host tests of Compose modules read Compose resources, which are packaged as Android resources.
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    iosArm64()
    iosSimulatorArm64()
    // Platform capabilities and Room's database constructor are expect/actual classes, still opt-in.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("kotlin-test").get())
    }
}
