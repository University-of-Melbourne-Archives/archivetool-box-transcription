package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.config.Config
import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.pythoninterop.PythonModule
import au.edu.unimelb.archivetool.pythoninterop.extensions.invoke
import au.edu.unimelb.archivetool.pythoninterop.injectables.Logger as LoggerInjectable
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadataConverter
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import kotlin.collections.mapIndexed
import kotlin.io.path.pathString

class PythonOCRInteraction : OCRInteraction {
    val module by lazy { PythonModule(setOf("ocr.py"), setOf(LoggerInjectable())) }

    private val method_executeOCR by lazy {
        module.methods["executeOCR"].expect("OCR Module is missing function OCR::executeOCR")
    }

    private val method_processOutputText by lazy {
        module.methods["processOutputText"].expect(
            "OCR Module is missing function OCR::processOutputText"
        )
    }

    fun executeOCR(path: JobRelativePath, api: OpenAIAPIData): String {
        var output: String? = null
        try {
            output =
                method_executeOCR(path.absolute.pathString, api.baseUrl, api.apiKey, api.model)
                    .asString()
        } catch (e: Exception) {
            Log.trace("$e")
        }
        return output.expect(
            "OCR::executeOCR failed or returned either null or a non-string value."
        )
    }

    fun processOutputText(transcript: String): BoxMetadata? {
        var output: BoxMetadata? = null
        try {
            val py = method_processOutputText(transcript)
            output = BoxMetadataConverter.toJvmValue(py)
        } catch (e: Exception) {
            Log.trace("OCR::processOutputText threw: $e")
        }
        return output // NOTE: I think failing and returning a nullable here is fine.
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
                bounds.copy(rawMetadata = processOutputText(transcript))
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
                cropId to crop.copy(rawMetadata = processOutputText(transcript))
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
                    this.any { box ->
                        box.metadata == null ||
                            box.keyedBoundaries.values.any { it.rawMetadata == null }
                    }
            } ?: false
        }
        /*TODO: Add a check for if the generated crop etc exists.*/
    }
}
