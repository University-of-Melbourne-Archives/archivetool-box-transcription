import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

// SAFETY: If this is incorrectly typed, we don't want to compile anyway.
@Suppress("UNCHECKED_CAST")
val doodle: DependencyHandlerScope.() -> Unit = extensions.extraProperties["au.edu.unimelb.archivetool.doodleDependency"] as DependencyHandlerScope.() -> Unit

dependencies {
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.javacv)
    implementation(libs.onnx)
    implementation(libs.openai)
    implementation(libs.csv)
    doodle()
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}
