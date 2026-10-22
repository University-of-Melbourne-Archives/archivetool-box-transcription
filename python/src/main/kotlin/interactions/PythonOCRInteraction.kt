package au.edu.unimelb.archivetool.python.interactions

import au.edu.unimelb.archivetool.config.Config
import au.edu.unimelb.archivetool.config.OpenAIAPIData
import au.edu.unimelb.archivetool.python.interop.PythonModule
import au.edu.unimelb.archivetool.python.interop.converters.BoxMetadataConverter
import au.edu.unimelb.archivetool.python.interop.extensions.invoke
import au.edu.unimelb.archivetool.python.interop.injectables.DisableTrustStore
import au.edu.unimelb.archivetool.python.interop.injectables.Logger
import au.edu.unimelb.archivetool.switchboard.interactions.OCRInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox.Companion.BOX_CROP_NAME
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobState
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.wrap.OpenAI
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.io.path.pathString

class PythonOCRInteraction : OCRInteraction {
    override fun mayRequireConversion(): Boolean = true

    override fun supported(): Image.FileType.Supported = OpenAI.formatRequirements

    val module by lazy { PythonModule(setOf("ocr.py"), setOf(Logger(), DisableTrustStore)) }

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
            Log.trace("OCR::processOutputText threw: $e\n${e.printStackTrace()}")
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
