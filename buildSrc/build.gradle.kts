plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`

    alias(libs.plugins.ktfmtGradlePlugin)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
    implementation(plugin(libs.plugins.ktfmtGradlePlugin))

    // Segmentation Model Ports
   implementation("com.microsoft.onnxruntime:onnxruntime:1.30.0")  // 1.30.0 is the latest 1.x version as of 10/10/2026
   implementation("org.openpnp:opencv:4.9.0-0")                    // bundles Windows/Linux/macOS natives
}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
// Derived from https://docs.gradle.org/current/userguide/version_catalogs.html#sec:buildsrc-version-catalog
fun DependencyHandlerScope.plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }