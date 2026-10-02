package buildsrc.convention.python

import buildsrc.convention.util.AutoFormatter
import buildsrc.convention.util.maybeRegister
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

object AutoFormat: AutoFormatter() {
    override fun formatTask(project: Project): TaskProvider<*> =
        project.tasks.maybeRegister("python-format") {
            group = "formatting"
            description = "Runs black on all Python source sets"
        }

    override fun checkTask(project: Project): TaskProvider<*> =
        project.tasks.maybeRegister("python-check") {
            group = "verification"
            description = "Runs black --check on all Python source sets"
        }

    override fun configure(project: Project) = with(project) {
        super.configure(project)

        val pythonSources = extensions.getByType<PythonSourcesExtension>()

        pythonSources.sourceDirectorySets.all {
            val set = this

            val files: FileCollection = project.files(srcDirs)

            val formatTask = tasks.register<Exec>("python-format-$name") {
                group = "formatting"
                description = "Runs black on the $name Python sources"

                inputs.files(files)
                    .withPropertyName("pythonSources")
                    .withPathSensitivity(PathSensitivity.RELATIVE)

                onlyIf { files.files.any { it.exists() } }

                // black rewrites files in place, so the sources are also the outputs
                files.files.forEachIndexed { index, dir ->
                    outputs.dir(dir)
                        .withPropertyName("formattedPythonSources$index")
                }

                try {
                    commandLine(
                        listOf("black") + files.files.map { it.absolutePath }
                    )
                } catch (e: Exception) {
                    error("Cannot autoformat Python files! You likely need to install Black.")
                }
            }
            formatTask(project).configure {
                dependsOn(formatTask)
            }

            val checkTask = tasks.register<Exec>("python-check-$name") {
                group = "verification"
                description = "Checks that the $name Python sources are formatted with black"

                inputs.files(files)
                    .withPropertyName("pythonSources")
                    .withPathSensitivity(PathSensitivity.RELATIVE)

                onlyIf { files.files.any { it.exists() } }

                commandLine(listOf("black", "--check") + files.files.map { it.absolutePath })
            }

            checkTask(project).configure {
                dependsOn(checkTask)
            }
        }
    }.let{}
}

