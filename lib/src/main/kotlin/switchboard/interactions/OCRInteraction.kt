package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.config.Config
import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.ocr.OCRService
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.switchboard.jobstate.toBoxMetadata
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.wrap.OpenAI
import kotlin.collections.mapIndexed

class KotlinOCRInteraction : OCRInteraction {
    override fun mayRequireConversion(): Boolean = true

    override fun supported(): Image.FileType.Supported = OpenAI.formatRequirements

    private class ServiceContainer(settings: OpenAIAPIData) {
        val inner by lazy { OCRService(settings) }
    }

    private var container: ServiceContainer? = null

    fun getService(settings: OpenAIAPIData): OCRService =
        (container ?: ServiceContainer(settings)).inner

    fun executeOCR(path: JobRelativePath, api: OpenAIAPIData): String {
        return getService(api).executeOCR(path)
    }

    fun processOutputText(transcript: String, api: OpenAIAPIData): BoxMetadata? {
        try {
            Log.trace("Transcribed as $transcript")
            return getService(api).processOutputText(transcript).toBoxMetadata()
        } catch (e: Exception) {
            Log.trace("OCR::processOutputText threw: $e\n${e.printStackTrace()}")
        }
        return null
        // NOTE: I think failing and returning a nullable here is fine.
        // Some labels will not have
        //    .expect("OCR::processOutputText failed or returned either null or a non-string
        // value.")
    }

    fun OCRBox(
        jpath: JobRelativePath,
        boxId: String,
        box: ArchiveBox,
        api: OpenAIAPIData,
    ): Boundary? =
        box.boxBoundary
            .takeIf { it.rawMetadata == null }
            ?.let { bounds ->
                val genPath = JobState.cropPath(jpath, boxId, BOX_CROP_NAME)
                val transcript = executeOCR(genPath, api)
                bounds.copy(rawMetadata = processOutputText(transcript, api))
            }

    fun OCRLabel(
        jpath: JobRelativePath,
        boxId: String,
        box: ArchiveBox,
        api: OpenAIAPIData,
    ): List<Boundary> =
        box.labelBoundaries
            .mapIndexed(ArchiveBox::keyedLabel)
            .map { (cropId, crop) ->
                if (crop.rawMetadata != null) return@map cropId to crop
                val genPath = JobState.cropPath(jpath, boxId, cropId)
                val transcript = executeOCR(genPath, api)
                cropId to crop.copy(rawMetadata = processOutputText(transcript, api))
            }
            .map { it.second }

    override fun generateOCR(
        files: Map<JobRelativePath, JobFile>,
        config: Config,
    ): Map<JobRelativePath, JobFile> {
        val out = mutableMapOf<JobRelativePath, JobFile>()
        files.forEach { (jpath, file) ->
            file.vertexes!!.forEach { (boxId, box) ->
                val boxBoundary = OCRBox(jpath, boxId, box, config.ocrApiSettings)
                val labelBoundaries = OCRLabel(jpath, boxId, box, config.ocrApiSettings)

                val meta = BoxMetadata.bestGuess(boxBoundary, labelBoundaries)

                out[jpath] = file
                out[jpath]!!.apply {
                    vertexes!![boxId] =
                        box.copy(
                            boxBoundary = boxBoundary ?: box.boxBoundary,
                            labelBoundaries = labelBoundaries,
                            metadata = meta,
                        )
                }
            }
        }
        return out
    }
}

class MockOCRInteraction : OCRInteraction {
    override fun generateOCR(
        files: Map<JobRelativePath, JobFile>,
        config: Config,
    ): Map<JobRelativePath, JobFile> {
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
    fun generateOCR(
        files: Map<JobRelativePath, JobFile>,
        config: Config,
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean {
        return file.second.let { jobFile ->
            jobFile.vertexes?.values?.run {
                isNotEmpty() &&
                    any { box ->
                        box.metadata == null ||
                            box.keyedBoundaries.values.any { it.rawMetadata == null }
                    }
            } ?: false && jobFile.approved
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
