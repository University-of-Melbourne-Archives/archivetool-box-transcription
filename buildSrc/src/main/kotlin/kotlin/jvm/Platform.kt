package buildsrc.convention.kotlin.jvm

import buildsrc.convention.util.Module
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.bytedeco.javacpp.Loader;

object Platform: Module {
    override val plugins: Set<String> = setOf("org.bytedeco.gradle-javacpp-platform")
    fun doodleHost(): String {
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

    override fun configure(project: Project) = project.run {
        val platform by lazy {
            findProperty("archivetool.platform")?.toString()?.trim()
        }

        // TODO: There is a newer version of Doodle, but it has breaking changes.
        val doodleDependency: DependencyHandlerScope.() -> Unit = {
            val doodleVersion = "0.11.5"
            val doodlePlatforms = listOf("macos-x64", "macos-arm64", "linux-x64", "linux-arm64", "windows-x64")

            platform
                ?.replace("macosx", "macos")
                ?.replace("x86_64", "x64")
                ?.let { platform ->
                    val platformName = platform.removeSuffix("-gpu").takeIf { doodlePlatforms.contains(it) } ?: doodleHost().takeIf { platform == "host" }

                    platformName?.let{
                        add("api", "io.nacular.doodle:desktop-jvm-$it:$doodleVersion")
                        Unit
                    }
                } ?: doodlePlatforms.forEach {
                    add("api", "io.nacular.doodle:desktop-jvm-$it:$doodleVersion")
                }
        }

        extensions.extraProperties["au.edu.unimelb.archivetool.doodleDependency"] = doodleDependency

        val javaCppPlatforms = listOf(
            "android-arm",
            "android-arm64",
            "android-x86",
            "android-x86_64",
            "ios-arm64",
            "ios-x86_64",
            "linux-armhf",
            "linux-arm64",
            "linux-ppc64le",
            "linux-x86",
            "linux-x86_64",
            "macosx-arm64",
            "macosx-x86_64",
            "windows-arm64",
            "windows-x86",
            "windows-x86_64"
        )

        extensions.extraProperties["javacppPlatform"] = platform?.let {
                if (javaCppPlatforms.contains(it.removeSuffix("-gpu"))) {
                    it
                } else if (it == "host") {
                    Loader.Detector.getPlatform()
                } else null
            } ?: javaCppPlatforms.joinToString(",")
    }
}