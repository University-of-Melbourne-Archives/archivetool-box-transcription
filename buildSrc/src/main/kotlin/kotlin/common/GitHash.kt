package buildsrc.convention.kotlin.common

import buildsrc.convention.util.Module
import buildsrc.convention.util.maybeRegister
import org.gradle.api.Project
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.cc.base.logger
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.of
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory


const val TRIPLE_QUOTE = "\"\"\""

// This needs to be as a separate ValueSource, trust me; I tried.
abstract class IsGitInstalledSource : ValueSource<Boolean, ValueSourceParameters.None> {
    override fun obtain(): Boolean {
        return try {
            val process = ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start()

            // Drain output so the process can't block on a full buffer
            process.inputStream.use { it.readBytes() }

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("check for git installation timed out (non-fatal)")
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            logger.warn("git is not installed or something else went wrong (non-fatal)")
            val stackTrace = StringWriter().use { s ->
                PrintWriter(s).use { p ->
                    e.printStackTrace(p)
                }
                s.toString()
            }
            logger.debug("stack trace: $stackTrace")
            false
        }
    }
}


object GitHash : Module {
    override fun configure(project: Project) = with(project) {
        val gitDir = "${rootDir}/.git"

        val generateHashConstant = tasks.maybeRegister("get-git-hash") {
            val gitInstalled: Boolean = providers.of(IsGitInstalledSource::class) {}.get().also {
                if (!it) {
                    logger.warn("\tthe version of the program will be 'unknown'")
                }
            }
            val repoExists = Path(gitDir).takeIf { it.exists() && it.isDirectory() } != null
            val hash =
                project.providers.takeIf { gitInstalled }
                    ?.exec {
                        workingDir(rootDir)
                        commandLine("git", "rev-parse", "--short", "HEAD")
                        isIgnoreExitValue = true
                    }
                    ?.standardOutput?.asText
                    ?.map { stdout -> stdout.takeUnless {
                            // NOTE: The "fatal" message actually prints to stderr
                            //it.startsWith("fatal:")
                            it.isEmpty().also { cond ->
                                if (cond)
                                    logger.warn("git could not find a repository\n\tthe version of the program will be 'unknown'")
                            }
                        }?.let { maybeStdout ->
                            maybeStdout.trim() + (
                                    project
                                        .providers
                                        .exec {
                                            workingDir(rootDir)
                                            commandLine("git", "status", "--porcelain", "--untracked-files=no")
                                            isIgnoreExitValue = true
                                        }
                                        .standardOutput.asText.get()
                                        .takeIf {it.isEmpty()} ?: " (dirty working tree)"
                                    )
                        } ?: "unknown (no repository)"
                    }?.get() ?: "unknown (git not installed)"

            val outDir = layout.buildDirectory.dir("generated/git")

            val repoUrl = findProperty("archivetool.repository_url")
                ?.toString()
                ?.trim()
                ?.also { if (it.contains(TRIPLE_QUOTE)) error("Repository URL was unable to be escaped.") }

            if (repoExists)
                inputs.dir(gitDir)
            outputs.dir(outDir)
            doLast {
                val f = outDir.get().file("util/Git.generated.kt").asFile
                f.parentFile.mkdirs()
                f.writeText(
                    """
                    package au.edu.unimelb.archivetool.util
        
                    object Git {
                        const val HASH: String = "$hash";
                        const val REPOSITORY_URL = $TRIPLE_QUOTE$repoUrl$TRIPLE_QUOTE;
                    }
                    """.trimIndent()
                )
            }
        }

        val sourceSets = extensions.getByType<SourceSetContainer>()
        sourceSets.getByName("main") {
            java.srcDir(generateHashConstant)
        }
    }.let{}
}