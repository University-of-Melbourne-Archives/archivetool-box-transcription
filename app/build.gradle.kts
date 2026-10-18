plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    implementation(project(":lib"))
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlin2plantuml)
}

tasks.withType<CreateStartScripts> { applicationName = "archivetool" }

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "au.edu.unimelb.archivetool.app.AppKt"

    // Enable Bytecode JIT; See python/build.gradle.kts
    if ((providers.gradleProperty("enablePython").orNull ?: "false").toBoolean())
        applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI")
}

distributions {
    main {
        distributionBaseName = "archivetool-cli"
        contents {
            into("") { from("../README.md") }
            into("") { from("../LICENSE") }
            into("") { from("../THIRD_PARTY.md") }
        }
    }
}
