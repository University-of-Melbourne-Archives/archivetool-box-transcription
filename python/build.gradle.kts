import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)

    // Apply Python-adjacent libraries (No longer used)
    id("buildsrc.convention.python")
    alias(libs.plugins.graalpy)
}

graalPy {
    resourceDirectory =
        extensions.extraProperties["au.edu.unimelb.archivetool.pythoninterop.vfsDir"] as String
    packages = setOf("openai==3.16.*", "certifi>=2026.7.22") // Python dependencies
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog
    // (`gradle/libs.versions.toml`).
    api(project(":lib"))
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.bundles.polyglot)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

// Enable bytecode -> machinecode JIT for faster python execution.
// It is stuck as 'experimental' due to the associated flag `-XX:+UseJVMCICompiler` in the same JEP,
// but should be stable in practice.
tasks.named<Test>("test") { jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI") }
