plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Project "app" depends on project "lib".
    // (Project paths are separated with ":", so ":lib"
    //		refers to the top-level "lib" project.)
    implementation(project(":lib"))

    implementation(libs.bundles.clikt)
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class
    //		with FQN `au.edu.unimelb.archivetool.app.AppKt`.)
    mainClass = "au.edu.unimelb.archivetool.app.AppKt"

    // Enable Bytecode JIT; See lib/build.gradle.kts
    applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI")
}
