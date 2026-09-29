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

val doodleVersion = "0.11.5"

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog
    // (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.bundles.polyglot)
    implementation(libs.javacv)
    implementation(libs.csv)
    api("io.nacular.doodle:desktop-jvm-${osTarget()}:$doodleVersion")
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

fun osTarget(): String {
    val osName = System.getProperty("os.name")
    val targetOs =
        when {
            osName == "Mac OS X" -> "macos"
            osName.startsWith("Win") -> "windows"
            osName.startsWith("Linux") -> "linux"
            else -> error("Unsupported OS: $osName")
        }

    val targetArch =
        when (val osArch = System.getProperty("os.arch")) {
            "x86_64",
            "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported arch: $osArch")
        }

    return "$targetOs-$targetArch"
}

// Enable bytecode -> machinecode JIT for faster python execution.
// It is stuck as 'experimental' due to the associated flag `-XX:+UseJVMCICompiler` in the same JEP,
// but should be stable in practice.
tasks.named<Test>("test") { jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI") }
