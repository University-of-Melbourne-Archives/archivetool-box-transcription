package buildsrc.convention.kotlin.common

import buildsrc.convention.util.AutoFormatter
import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

object AutoFormat: AutoFormatter() {
     override val plugins = setOf(
        "com.ncorti.ktfmt.gradle"
     )

    override fun formatTask(project: Project): TaskProvider<*> {
        val task by lazy { project.tasks.named("ktfmtFormat") }
        return task
    }

    override fun checkTask(project: Project): TaskProvider<*> {
        val task by lazy { project.tasks.named("ktfmtCheck") }
        return task
    }

    override fun configure(project: Project) {
        super.configure(project)

        // Configure ktfmt to use style described at kotlinlang.org/docs/coding-conventions.html
        project.extensions.configure<KtfmtExtension>() {
            kotlinLangStyle()
        }

        // Ignore generated files.
        project.tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask> {
            exclude("*.generated.kt")
        }
        project.tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask> {
            exclude("*.generated.kt")
        }
    }
}