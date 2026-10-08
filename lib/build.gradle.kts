import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.python")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.graalpy)
}

graalPy {
    resourceDirectory = "GRAALPY-VFS/${rootProject.name}/${project.name}"
    packages = setOf("openai==3.16.*") // Python dependencies (eg. "qrcode==7.4.2")
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog
    // (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.bundles.polyglot)
    implementation(libs.javacv)
    implementation(libs.csv)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

// Enable bytecode -> machinecode JIT for faster python execution.
// It is stuck as 'experimental' due to the associated flag `-XX:+UseJVMCICompiler` in the same JEP,
// but should be stable in practice.
tasks.named<Test>("test") { jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI") }
