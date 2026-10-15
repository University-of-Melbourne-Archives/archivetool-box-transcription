// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention
import buildsrc.convention.python.AutoFormat
import buildsrc.convention.python.CopySources
import buildsrc.convention.python.CreateSourceDirectorySet
import buildsrc.convention.python.GraalPyVFS
import buildsrc.convention.util.Module
import buildsrc.convention.util.Module.Companion.apply

val modules: Set<Module> = setOf(CreateSourceDirectorySet, AutoFormat, CopySources, GraalPyVFS)
modules.apply(project)


