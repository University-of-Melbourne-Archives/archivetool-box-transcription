package buildsrc.convention.util

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope

interface Module {
    val plugins: Set<String> get() = setOf()
    fun configure(project: Project)

    companion object {
        fun Set<Module>.apply(project: Project) = with(project) {
            forEach { module ->
                module.plugins.forEach { pluginManager.apply(it) }
                module.configure(project)
            }
        }
    }
}