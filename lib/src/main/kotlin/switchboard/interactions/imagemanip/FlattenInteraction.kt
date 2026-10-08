package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.CV
import au.edu.unimelb.archivetool.util.CV.writeImage
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import org.bytedeco.opencv.opencv_core.Mat

class FlattenInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val outFiles: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

        files.forEach { (jpath, info) ->
            val _in = CV.readImage(jpath.absolute)
            info.vertexes?.forEach { (id, box) ->
                box.labelBoundaries.forEachIndexed { i, b ->
                    ArchiveBox.keyedLabel(i, b).let { (cropId, boundary) ->
                        val srcQuad = boundary.points

                        val destQuad = srcQuad.bestBoundingBox()

                        // The size we are projecting to.
                        val outRect: Rectangle =
                            Rectangle.create(0, 0, destQuad.width, destQuad.height)

                        val cropped = CV.perspectiveWarp(_in, srcQuad, outRect)

                        val outPath = JobState.cropPath(jpath, id, cropId)
                        cropped.writeImage(outPath.absolute)

                        cropped.release()
                    }
                }
                box.boxBoundary.let { b ->
                    (BOX_CROP_NAME to b).let { (cropId, boundary) ->
                        val srcQuad = boundary.points

                        val destQuad = srcQuad.bestBoundingBox()

                        // The size we are projecting to.
                        val outRect: Rectangle =
                            Rectangle.create(0, 0, destQuad.width, destQuad.height)

                        val cropped: Mat
                        val blanked: Mat

                        CV.PerspectiveTransformMatrix.create(srcQuad, outRect).use { mat ->
                            cropped = CV.perspectiveWarp(_in, mat)

                            blanked =
                                LabelBlanker.blankLabels(cropped, box.labelBoundaries) {
                                    val newPoints = mapPoints { CV.perspectiveTransformRel(it, mat) }
                                    newPoints.confinedTo(outRect)
                                }
                        }

                        val outPath = JobState.cropPath(jpath, id, cropId)
                        blanked.writeImage(outPath.absolute)

                        cropped.release()
                        blanked.release()
                    }
                }
                outFiles[jpath] = info
            }
            _in.release()
        }

        return outFiles
    }
}
