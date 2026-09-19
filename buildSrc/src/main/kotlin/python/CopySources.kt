package buildsrc.convention.python

import buildsrc.convention.util.Module
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

object CopySources: Module {
    override fun configure(project: Project) = with(project) {
        plugins.withType<JavaBasePlugin> {
            val sourceSets = extensions.getByType<SourceSetContainer>()

            val pythonSources = extensions.getByType<PythonSourcesExtension>()

            pythonSources.sourceDirectorySets.all {
                sourceSets.matching { it.name == name }.all {
                    val copyTask = tasks.register<Copy>("python-copy-$name") {
                        from(srcDirs)
                        into(output.resourcesDir!!.resolve("python"))
                    }

                    // We need this to run before anything that requres the classpath to already be in place.
                    tasks.named(getTaskName("process", "resources")) {
                        finalizedBy(copyTask)
                    }
                    tasks.named(getTaskName("", "classes")) {
                        dependsOn(copyTask)
                    }
                }
            }
        }
    }.let{}
}