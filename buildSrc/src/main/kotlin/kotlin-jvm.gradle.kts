// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")

    // Enable autoformat on build.
    id("com.ncorti.ktfmt.gradle")
}

val libs = the<VersionCatalogsExtension>().named("libs")


kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(21)
}

// Configure ktfmt to use style described at kotlinlang.org/docs/coding-conventions.html
ktfmt {
    kotlinLangStyle()
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
