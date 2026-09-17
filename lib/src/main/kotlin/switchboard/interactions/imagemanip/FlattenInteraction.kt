package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.CV
import au.edu.unimelb.archivetool.util.CV.writeImage
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import au.edu.unimelb.archivetool.util.geometry.Rectangle.Companion.toRectangle
import org.bytedeco.opencv.opencv_core.Rect

class FlattenInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val outFiles: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

        files.forEach { (jpath, info) ->
            val _in = CV.readImage(jpath.absolute)
            info.vertexes?.forEach { (id, box) ->
                box.keyedBoundaries.forEach { cropId, boundary ->
                    val srcQuad = boundary.points

                    val destQuad = srcQuad.bestBoundingBox()
                    // The size we are projecting to.
                    val outRect: Rectangle =
                        Rect(0, 0, destQuad.width, destQuad.height).toRectangle()

                    val cropped = CV.perspectiveWarp(_in, srcQuad, outRect)

                    val outPath = JobState.cropPath(jpath, id, cropId)
                    cropped.writeImage(outPath.absolute)

                    outFiles[jpath] = info
                }
            }
        }

        return outFiles
    }
}
