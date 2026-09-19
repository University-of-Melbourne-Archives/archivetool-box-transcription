package buildsrc.convention.python

import buildsrc.convention.util.Module
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.create
import javax.inject.Inject

object CreateSourceDirectorySet: Module {
    override fun configure(project: Project) =
        project.extensions.create<PythonSourcesExtension>("pythonSources").let {}
}

abstract class PythonSourcesExtension @Inject constructor(
    private val objects: ObjectFactory
) {
    val sourceDirectorySets: NamedDomainObjectContainer<SourceDirectorySet> =
        objects.domainObjectContainer(SourceDirectorySet::class.java) { name ->
            objects.sourceDirectorySet(name, "$name Python sources")
        }

    init {
        sourceDirectorySets.create("main") { srcDir("src/main/python"); include("**/*.py") }
        sourceDirectorySets.create("test") { srcDir("src/test/python"); include("**/*.py") }
    }
}




