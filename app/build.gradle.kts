plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Image processing and workflow orchestration
    implementation(project(":lib"))

    // Command-line interface
    implementation(libs.bundles.clikt)

    // UML Generation Script
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlin2plantuml)

    // CLI unit tests
    testImplementation(kotlin("test"))
}

tasks.withType<CreateStartScripts> { applicationName = "archivetool" }

application {
    // Define the Fully Qualified Name for the application main class
    // App.kt is compiled to au.edu.unimelb.archivetool.app.AppKt.
    mainClass = "au.edu.unimelb.archivetool.app.AppKt"

    // Enable Bytecode JIT; See python/build.gradle.kts
    if ((providers.gradleProperty("archivetool.python").orNull ?: "false").toBoolean())
        applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI")
}

distributions {
    main {
        distributionBaseName = "archivetool-cli"
        contents {
            into("") { from("../README.md") }
            into("") { from("../LICENSE") }
            into("") { from("../CONTRIBUTING") }
            into("/docs") { from("../docs") }
            into("") { from("../THIRD_PARTY.md") }
        }
    }
}
