package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.CV
import au.edu.unimelb.archivetool.util.CV.writeImage

class CropInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val outFiles: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

        files.forEach { (jpath, info) ->
            info.vertexes?.forEach { (id, box) ->
                box.keyedBoundaries.forEach { cropId, boundary ->
                    val _in = CV.readImage(jpath.absolute)

                    val cropped = _in.apply(boundary.points.bestBoundingBox().intoCVRect())

                    val outPath = JobState.cropPath(jpath, id, cropId)
                    cropped.writeImage(outPath.absolute)

                    outFiles[jpath] = info
                }
            }
        }

        return outFiles
    }
}
