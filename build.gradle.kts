plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover) apply false
}

// Module dependency rules from the architecture plan, as per-module allowlists; a module without a rule fails.
val model = ":core:model"
val domain = ":core:domain"
val testing = ":core:testing"

fun allowedProjectDependencies(path: String, allPaths: Set<String>): Set<String>? = when {
    path == ":core" || path == ":feature" -> emptySet() // directory-only parent projects
    path == model -> emptySet()
    path == domain -> setOf(model)
    path == ":core:resources" -> setOf(model, domain)
    path == ":core:designsystem" -> setOf(model, domain, ":core:resources")
    path in setOf(":core:platform", ":core:database", ":core:network", ":core:preferences") -> setOf(model, domain)
    path == ":core:data" ->
        setOf(model, domain, ":core:database", ":core:network", ":core:preferences", ":core:platform")
    path == testing -> setOf(model, domain, ":core:platform")
    path.startsWith(":feature:") -> setOf(model, domain, ":core:designsystem", ":core:resources")
    path == ":shared" -> allPaths - setOf(":shared", ":androidApp", testing)
    path == ":androidApp" -> setOf(":shared")
    path == ":benchmark" -> setOf(":androidApp")
    else -> null
}

// Named exceptions to the per-module allowlists, each for exactly one configuration:
// - D31: the benchmark build type's measurement seeder writes through the app's database; it never ships in release.
// - The baseline-profile plugin's consumer configuration names the module that generates the profile.
val configurationExceptions = mapOf(
    ":androidApp" to "benchmarkImplementation" to setOf(":core:database"),
    ":androidApp" to "baselineProfile" to setOf(":benchmark"),
)

// Rule 1 (plus decision D3): the pure modules' main code may use only these external libraries.
fun isAllowedExternal(path: String, group: String?, name: String): Boolean =
    (group == "org.jetbrains.kotlin" && name.startsWith("kotlin-")) ||
        "$group:$name" in setOf("org.jetbrains.kotlinx:kotlinx-datetime", "org.jetbrains.kotlinx:kotlinx-coroutines-core") ||
        (path == domain && "$group:$name" == "androidx.paging:paging-common")

// Rules 2 and 3: UI-side modules never use the data-side libraries directly.
val uiSideModules = setOf(":core:designsystem", ":core:resources")
val dataSideGroups = setOf("androidx.room", "androidx.sqlite", "androidx.datastore", "io.ktor", "com.fleeksoft.ksoup")

// Test source sets and their configurations: test, commonTestImplementation, androidHostTestCompileClasspath, ...
fun isTestConfiguration(name: String): Boolean = name.startsWith("test") || name.contains("Test")

// shortcut: only declared dependencies are checked; dependencies added or substituted while a classpath resolves
// (withDependencies, dependency substitution) are not seen; upgrade to resolved-graph checks if that route is used.
// shortcut: data-side libraries that arrive transitively (e.g. coil-network-ktor3 -> Ktor) are not seen; upgrade to
// resolved-graph checks if a feature starts using such a library.
val moduleGraphViolations = provider {
    val allPaths = subprojects.map { it.path }.toSet()
    subprojects.flatMap { project ->
        val path = project.path
        val allowed = allowedProjectDependencies(path, allPaths)
            ?: return@flatMap listOf("$path: no dependency rule covers this module")
        // Kotlin's SwiftPM lock-file configurations list every project in the build; they are tooling, not dependencies.
        // Kover's agent configurations hold the coverage agent, not code the module uses.
        val configurations = project.configurations
            .filterNot { it.name.startsWith("swiftPMDependencies") || it.name.startsWith("kover") }
        configurations.flatMap { configuration ->
            // Iterate, not withType: Kotlin's top-level dependencies {} block adds lazily, and only iteration realizes it.
            val dependencies = configuration.dependencies.toList()
            val isTest = isTestConfiguration(configuration.name)
            val where = "$path (${configuration.name})"
            dependencies.mapNotNull { dependency ->
                when {
                    dependency is ProjectDependency -> {
                        val exception = configurationExceptions[path to configuration.name].orEmpty()
                        if (dependency.path == path || dependency.path in allowed || dependency.path in exception ||
                            (isTest && dependency.path == testing)
                        ) {
                            null
                        } else {
                            "$where -> ${dependency.path}: not allowed"
                        }
                    }
                    path in setOf(model, domain) && !isTest && dependency is FileCollectionDependency ->
                        "$where -> file dependency: not allowed in a pure module"
                    path in setOf(model, domain) && !isTest && dependency is ExternalModuleDependency &&
                        !isAllowedExternal(path, dependency.group, dependency.name) ->
                        "$where -> ${dependency.group}:${dependency.name}: external library not allowed"
                    (path.startsWith(":feature:") || path in uiSideModules) && !isTest &&
                        dependency is ExternalModuleDependency && dependency.group in dataSideGroups ->
                        "$where -> ${dependency.group}:${dependency.name}: data-side library not allowed"
                    else -> null
                }
            }
        }
    }
}

val checkModuleGraph = tasks.register("checkModuleGraph") {
    description = "Fails when a dependency breaks the architecture plan's module rules."
    val violations = moduleGraphViolations
    doLast {
        check(violations.get().isEmpty()) { "Module graph violations:\n" + violations.get().joinToString("\n") }
    }
}

tasks.register("check") {
    dependsOn(checkModuleGraph)
}
