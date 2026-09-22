package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.pythoninterop.PythonModule
import au.edu.unimelb.archivetool.pythoninterop.extensions.invoke
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadataConverter
import au.edu.unimelb.archivetool.pythoninterop.injectables.Logger as LoggerInjectable
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import kotlin.collections.mapIndexed
import kotlin.io.path.pathString

class PythonOCRInteraction : OCRInteraction {
    val module by lazy {
        PythonModule(folder = TODO("Put Module Path Here"), setOf(LoggerInjectable()))
    }
        PythonModule(setOf("ocr.py"), setOf(LoggerInjectable()))
    }

    override fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        val out = mutableMapOf<JobRelativePath, JobFile>()
        files.forEach { (jpath, file) ->
            file.vertexes!!.forEach { (boxId, box) ->
                val boxBoundary = OCRBox(jpath, boxId, box)
                val labelBoundaries = OCRLabel(jpath, boxId, box)

                val meta = BoxMetadata.bestGuess(boxBoundary, labelBoundaries)

                out[jpath] = file
                out[jpath]!!.apply {
                    vertexes!![boxId] = box.copy(
                        boxBoundary = boxBoundary ?: box.boxBoundary,
                        labelBoundaries = labelBoundaries,
                        metadata = meta
                    )
                }
            }
        }
        return out
    }
}

class MockOCRInteraction : OCRInteraction {
    override fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile> {
        Log.error("Running Mock OCR, nothing will be done.")
        files.forEach { (path, file) ->
            file.vertexes?.forEach { (boxId, box) ->
                box.keyedBoundaries.forEach { (cropId, boundary) ->
                    Log.debug("Transcribing ${path.relative}::$boxId-$cropId")
                }
            }
        }
        return mapOf()
    }
}

interface OCRInteraction : Interaction {
    fun generateOCR(files: Map<JobRelativePath, JobFile>): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean {
        return file.second.let { jobFile ->
            jobFile.vertexes?.values?.run {
                isNotEmpty() &&
                    this.any { box ->
                        box.metadata == null ||
                        box.keyedBoundaries.values.any { it.rawMetadata == null }
                    }
            } ?: false
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
