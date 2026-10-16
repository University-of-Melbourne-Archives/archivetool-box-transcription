import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

val doodleVersion = "0.11.5"

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog
    // (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.javacv)
    implementation(libs.csv)
    api("io.nacular.doodle:desktop-jvm-${osTarget()}:$doodleVersion")
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)

    // Segmentation Model Dependencies
    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")

    // For OpenAI API (Java)
    implementation("com.openai:openai-java:4.81.0")
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
