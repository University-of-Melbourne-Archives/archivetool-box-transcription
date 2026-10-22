package buildsrc.convention.python

import buildsrc.convention.util.Module
import buildsrc.convention.util.maybeRegister
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType

object GraalPyVFS: Module {
    override fun configure(project: Project) = with(project) {
        val vfsDir = "GRAALPY-VFS/${rootProject.name}/${project.name}"

        val generateVfsConstant = tasks.maybeRegister("write-graalpy-vfs-${rootProject.name}-${project.name}") {
            val outDir = layout.buildDirectory.dir("generated/graalpy-vfs")
            inputs.property("vfsDir", vfsDir)
            outputs.dir(outDir)
            doLast {
                val f = outDir.get().file("interop/GraalPyVFS.generated.kt").asFile
                f.parentFile.mkdirs()
                f.writeText(
                    """
                    package au.edu.unimelb.archivetool.python.interop
        
                    const val GRAALPY_VFS_PATH: String = "$vfsDir";
                    """.trimIndent()
                )
            }
        }

        val sourceSets = extensions.getByType<SourceSetContainer>()
        sourceSets.getByName("main") {
            java.srcDir(generateVfsConstant)
        }

        extensions.extraProperties["au.edu.unimelb.archivetool.pythoninterop.vfsDir"] = vfsDir
    }.let{}
}