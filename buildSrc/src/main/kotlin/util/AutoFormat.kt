package buildsrc.convention.util

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

abstract class AutoFormatter : Module {
    abstract fun formatTask(project: Project): TaskProvider<*>
    abstract fun checkTask(project: Project): TaskProvider<*>

    fun register(project: Project) = with(project) {
        val fmt = tasks.maybeRegister("fmt") {
            group = "formatting"
            description = "Runs all formatters"
        }

        val check = tasks.maybeRegister("checkFmt") {
            group = "verification"
            description = "Checks all formatters"
        }

        tasks.named("check") {
            dependsOn(check)
        }

        tasks.named("test") {
            mustRunAfter(check)
        }

        fmt.configure {
            dependsOn(formatTask(project))
        }

        check.configure {
            dependsOn(checkTask(project))
        }
    }

    override fun configure(project: Project) {
        register(project)
    }
}