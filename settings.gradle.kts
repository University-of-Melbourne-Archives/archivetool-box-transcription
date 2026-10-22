
import org.gradle.kotlin.dsl.invoke
import java.util.Properties

// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central and the Gradle Plugin Portal as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val localProperties: Map<String, String>? by lazy {
    file("local.properties")
        .takeIf {
            it.exists()
        }?.inputStream()?.use { stream ->
            Properties().apply {
                load(stream)
            }
        }?.entries?.associate { (k, v) ->
            k.toString() to v.toString()
        }
}

localProperties?.let {
    gradle.beforeProject {
        it
            // don't override anything already set via gradle.properties or -P
            .filter { (k, v) -> !project.hasProperty(k) }
            .forEach { (k, v) ->
                extensions.extraProperties[k] = v
            }
    }
}

// Include the `app`, `gui` and `lib` subprojects in the build.
// If there are changes in only one of the projects, Gradle will rebuild only the one that has changed.
// Learn more about structuring projects with Gradle - https://docs.gradle.org/8.7/userguide/multi_project_builds.html
include(":app")
include(":gui")
include(":lib")

if ((providers.gradleProperty("archivetool.python").orNull ?: localProperties?.get("archivetool.python") ?: "false").toBoolean()) {
    include(":python")
}

rootProject.name = "archivetool"