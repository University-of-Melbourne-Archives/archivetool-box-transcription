package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.switchboard.interactions.ImageManipulationInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.CV
import au.edu.unimelb.archivetool.util.CV.writeImage
import org.bytedeco.opencv.opencv_core.Mat

class CropInteraction : ImageManipulationInteraction {
    override fun manipulate(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val outFiles: MutableMap<JobRelativePath, JobFile> = mutableMapOf()

        files.forEach { (jpath, info) ->
            val _in = CV.readImage(jpath.absolute)
            info.vertexes?.forEach { (id, box) ->
                box.labelBoundaries.forEachIndexed { i, b ->
                    ArchiveBox.keyedLabel(i, b).let { (cropId, boundary) ->
                        var cropped = Mat()
                        _in.copyTo(cropped)
                        cropped = cropped.apply(boundary.points.bestBoundingBox().intoCVRect())

                        val outPath = JobState.cropPath(jpath, id, cropId)
                        cropped.writeImage(outPath.absolute)
                        cropped.release()
                    }
                }

                box.boxBoundary.let { b ->
                    (BOX_CROP_NAME to b).let { (cropId, boundary) ->
                        val bbox = boundary.points.bestBoundingBox()

                        val cropped = _in.apply(bbox.intoCVRect())

                        val blanked =
                            LabelBlanker.blankLabels(cropped, box.labelBoundaries) {
                                confinedTo(bbox)
                                val i = offsetBy(-bbox.xMin, -bbox.yMin)
                                i
                            }

                        val outPath = JobState.cropPath(jpath, id, cropId)
                        blanked.writeImage(outPath.absolute)

                        cropped.release()
                        blanked.release()
                    }
                }
            }
            _in.release()
        }

        return outFiles
    }
}
