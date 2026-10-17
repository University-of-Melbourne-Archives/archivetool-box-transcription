plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    // Core processing library
    implementation(project(":lib"))

    // CLI framework
    implementation(libs.bundles.clikt)

    // Switchboard dependencies
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxSerialization)
    implementation("io.github.kelvindev15:Kotlin2PlantUML:3.0.16")

    // CLI tests
    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class
    //		with FQN `au.edu.unimelb.archivetool.app.AppKt`.)
    mainClass = "au.edu.unimelb.archivetool.app.AppKt"

    // Enable Bytecode JIT; See lib/build.gradle.kts
    applicationDefaultJvmArgs = listOf("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI")
}
