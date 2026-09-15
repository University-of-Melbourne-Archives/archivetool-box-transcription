import java.io.File
import kotlin.io.copyRecursively
import kotlin.io.path.createParentDirectories
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.graalpy)
}

fun SourceSet.setupPythonTask(inputDir: String) =
    tasks.register("python-$name") {
        description =
            "Move Python files to the right directory. Any other steps should also go here."
        group = "build"

        // Register the project version as a task input, so Gradle can cache the task
        inputs.dir(inputDir)

        // tell Gradle about the output directory
        outputs.dir(temporaryDir)

        val outDir = File(temporaryDir, "python").toPath().apply { createParentDirectories() }

        doLast { File(inputDir).copyRecursively(outDir.toFile(), overwrite = true) }
    }

graalPy {
    packages = setOf() // Python dependencies (eg. "qrcode==7.4.2")
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog
    // (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.bundles.polyglot)
    implementation(libs.opencv)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

sourceSets {
    main {
        resources { srcDir(setupPythonTask("$projectDir/src/main/python").map { it.temporaryDir }) }
    }
    test {
        resources { srcDir(setupPythonTask("$projectDir/src/test/python").map { it.temporaryDir }) }
    }
}

// Enable bytecode -> machinecode JIT for faster python execution.
// It is stuck as 'experimental' due to the associated flag `-XX:+UseJVMCICompiler` in the same JEP,
// but should be stable in practice.
tasks.named<Test>("test") { jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI") }
