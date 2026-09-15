package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.CV
import au.edu.unimelb.archivetool.util.CV.writeImage
import kotlin.io.path.extension
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable

class CLAHEInteraction : ImageManipulationInteraction {
    val tileGridSize = Pair(8, 8)
    val clipLimit = 1.5

    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val outFiles: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

        files.forEach { (jpath, info) ->
            val generatedPaths = JobState.generatedCropPaths(jpath, info)
            if (generatedPaths.any { it.absolute.isReadable() }) {
                assert(
                    generatedPaths.all { it.absolute.isReadable() && it.absolute.isWritable() }
                ) {
                    "Not all crops generated successfully, re-run crop interaction."
                }
                generatedPaths.forEach { process(it) }
            } else {
                process(jpath, nonDestructive = true)
            }
            outFiles[jpath] = info
        }

        return outFiles
    }

    fun process(path: JobRelativePath, nonDestructive: Boolean = false) {
        val path = path.absolute
        val src = CV.readImage(path)
        val gray = CV.bgr2grayc1(src)

        val clahe = CV.clahe(gray, tileGridSize, clipLimit)

        clahe.writeImage(
            path.parent.resolve(
                path.toFile().nameWithoutExtension +
                    if (nonDestructive) {
                        ".clahe."
                    } else {
                        "."
                    } +
                    path.extension
            )
        )
    }
}
