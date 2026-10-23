package buildsrc.convention.kotlin.common

import buildsrc.convention.util.maybeRegister
import buildsrc.convention.util.Module
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType

object GitHash : Module {
    override fun configure(project: Project) = with(project) {
        val gitDir = "${rootDir}/.git"
        val generateHashConstant = tasks.maybeRegister("get-git-hash") {
            val hash = project.providers
                .exec {
                    workingDir(rootDir)
                    commandLine("git", "rev-parse", "--short", "HEAD")
                }
                .standardOutput.asText
                .map { stdout -> stdout.let {
                    it.takeUnless {
                        it.startsWith("fatal:")
                    } ?: "unknown (no repository)".also { println(stdout) }
                }.trim() }.get().let {
                    it + (project.providers
                        .exec {
                            workingDir(rootDir)
                            commandLine("git", "status", "--porcelain")
                        }.standardOutput.asText.get().takeIf {it.isEmpty()} ?: " (dirty working tree)")
                }
            val outDir = layout.buildDirectory.dir("generated/git")
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