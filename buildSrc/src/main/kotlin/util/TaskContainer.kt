package buildsrc.convention.util

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider


fun TaskContainer.maybeRegister(name: String, action: Action<in Task>): TaskProvider<Task> =
    names.contains(name).let { exists ->
        if (exists) named(name) else register(name, action)
    }
