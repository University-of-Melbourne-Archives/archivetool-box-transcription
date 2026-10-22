package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.math.geometry.Quad
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JobFile(val vertexes: MutableMap<String, ArchiveBox>? = null, val approved: Boolean = false) {
    val minConfidence: Double
        get() = vertexes?.values?.minOfOrNull { it.minConfidence } ?: -999.0
}

@Serializable
data class ArchiveBox(
    @SerialName("box") val boxBoundary: Boundary,
    @SerialName("sticker") // TODO: Do we care about knowing which label is the "canonical" one?
    val labelBoundaries: List<Boundary> =
        listOf(), // Also TODO: We may need to make sure this is enumerated.
    // This should also never be empty in practice, labelless boxes are out of scope.
    @SerialName("meta") val metadata: BoxMetadata? = null,
) {
    val keyedBoundaries: Map<String, Boundary> by lazy {
        mapOf(
            BOX_CROP_NAME to boxBoundary,
            *labelBoundaries.mapIndexed(::keyedLabel).toTypedArray(),
        )
    }
    val minConfidence: Double by lazy { keyedBoundaries.values.minOf { it.confidence } }

    companion object {
        const val BOX_CROP_NAME = "box"

        fun keyedLabel(index: Int, boundary: Boundary): Pair<String, Boundary> =
            "label-${index + 1}" to boundary
    }
}

@Serializable
data class Boundary(
    val points: Quad,
    val confidence: Double,
    // Contains the best-guess at a specific box's metadata.
    val rawMetadata: BoxMetadata? = null,
)
