plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils"
    // refers to the top-level "utils" project.)
    implementation(project(":lib"))
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerialization)
    implementation("io.github.kelvindev15:Kotlin2PlantUML:3.0.16")
}

tasks.withType<CreateStartScripts> { applicationName = "archivetool" }

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "au.edu.unimelb.archivetool.app.AppKt"

    // Enable Bytecode JIT; See lib/build.gradle.kts
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
