package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.pythoninterop.extensions.invoke
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.wrap.YOLO
import java.nio.file.Path
import kotlin.io.path.Path

class KotlinCreatePreviewInteraction : CreatePreviewInteraction {
    override fun mayRequireConversion(): Boolean = true

    override fun supported(): Image.FileType.Supported = YOLO.formatRequirements

    override fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path?,
        rejectConfidenceThreshold: Double,
    ): Map<JobRelativePath, JobFile> =
        files
            .map {
                BoxSegmentation.getBoxes(it, modelPath = weights, conf = rejectConfidenceThreshold)
            }
            .toMap()
}

/*
//TODO: This needs work.
class PythonCreatePreviewInteraction : CreatePreviewInteraction {
    override fun mayRequireConversion(): Boolean = true

    override fun supported(): Image.FileType.Supported = YOLO.formatRequirements

    val module by lazy { PythonModule(setOf("boxseg.py"), setOf(LoggerInjectable())) }

    private val method_getBoxes by lazy {
        module.methods["getBoxes"].expect(
            "Box Segmentation Module is missing function BoxSeg::getBoxes"
        )
    }

    fun copyModelFromResources(resourcePath: String): Path =
        copyFromResources(resourcePath, modelDirectory)

    companion object {
        val modelDirectory by lazy {
            createTempDirectory(
                "archivetool-models",
                PosixFilePermissions.fromString("rwxrwxr--").asFileAttribute(),
            )
        }

        val boundaryConverter =
            object : PythonToJvmConverter<Boundary> {
                override fun toJvmValue(polyglotValue: Value): Boundary =
                    polyglotValue.asMap().withStringKeys().let { map ->
                        Boundary(
                            points =
                                Quad.create(
                                    *map["points"]!!
                                        .asIterable()
                                        .map { it.asMap().withStringKeys() }
                                        .map { it["x"]!!.asInt() to it["y"]!!.asInt() }
                                        .toTypedArray()
                                ),
                            confidence = map["confidence"]!!.asFloat().toDouble(),
                        )
                    }
            }
    }

    fun getBoxes(
        baseDirectory: Path,
        imagePaths: List<String>,
        modelPath: String,
        confidenceFloor: Double,
        includeMask: Boolean = false,
    ): Map<JobRelativePath, JobFile> {
        var output: Map<JobRelativePath, JobFile>? = null
        try {
            val detections = method_getBoxes(imagePaths, modelPath, confidenceFloor, includeMask)
            output =
                detections.asMap().withStringKeys().mapEntries { (k, v) ->
                    JobRelativePath(baseDirectory, Path(k)) to
                        JobFile(
                            v.getHashValue("vertexes")
                                .asMap()
                                .withStringKeys()
                                .mapValues { (_, v) -> v.asMap().withStringKeys() }
                                .mapValues { (_, v) ->
                                    ArchiveBox(
                                        boxBoundary = boundaryConverter.toJvmValue(v["box"]!!),
                                        labelBoundaries =
                                            v["sticker"]!!
                                                .asIterable()
                                                .map { boundaryConverter.toJvmValue(it) }
                                                .toList(),
                                    )
                                }
                                .toMutableMap()
                        )
                }
        } catch (e: Exception) {
            Log.trace("$e")
        }
        return output.expect("BoxSeg::getBoxes failed.")
    }

    override fun createPreview(
        files: Set<JobRelativePath>,
        weights: String,
        rejectConfidenceThreshold: Double,
        /*
        edgeBufferSize: Double,
        rejectCentroidRadius: Double,
        */
    ): Map<JobRelativePath, JobFile> =
        getBoxes(
            files.first().rootDirectory,
            files.map { it.absolute.pathString },
            copyModelFromResources(weights).pathString,
            rejectConfidenceThreshold,
            false,
        )
}

 */

class MockCreatePreviewInteraction : CreatePreviewInteraction {
    override fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path?,
        rejectConfidenceThreshold: Double,
        /*
        edgeBufferSize: Double,
        rejectCentroidRadius: Double,
        */
    ): Map<JobRelativePath, JobFile> {
        Log.error("Using Mock Implementation of Create Preview. Nothing will be done.")
        val fileCountLimit = 15
        val fileList =
            files
                .map { it.relative }
                .joinToString(
                    prefix = "[\n\t",
                    postfix = "]",
                    separator = ",\n\t",
                    limit = fileCountLimit,
                    truncated = "(and ${files.count() - fileCountLimit} others)",
                )
        Log.debug("Files: $fileList")
        Log.debug("Weights Path: $weights")
        Log.debug("Confidence Threshold: $rejectConfidenceThreshold")
        /*
        Log.debug("Edge Buffer Size: $edgeBufferSize")
        Log.debug("Duplicate Boundary Detection Radius: $rejectCentroidRadius")
        */

        return mapOf()
    }
}

interface CreatePreviewInteraction : Interaction {
    fun createPreview(
        files: Set<JobRelativePath>,
        weights: Path? = null,
        // Flatly rejects boxes under this threshold
        rejectConfidenceThreshold: Double = 0.05,
        /*
        // Distance from edge for which if a box is fully within, should be rejected.
        edgeBufferSize: Double = TODO(),
        // Compares the centroids of boxes to detect duplicates (in px)
        rejectCentroidRadius: Double = TODO(),
        */
    ): Map<JobRelativePath, JobFile>

    override fun isEligible(file: Pair<JobRelativePath, JobFile>): Boolean =
        file.second.let { it.vertexes.isNullOrEmpty() && !it.approved }
}
