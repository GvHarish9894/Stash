import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Pure Kotlin (model, domain): Android consumes the JVM variant.
pluginManager.apply("org.jetbrains.kotlin.multiplatform")

val libs = the<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("kotlin-test").get())
    }
}
