plugins {
    `kotlin-dsl`
}

// compileOnly: the root build puts these plugins on the classpath once (`apply false`), so every module shares them.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}
