package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.geometry.Quad
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JobFile(val vertexes: MutableMap<String, ArchiveBox>? = null, val approved: Boolean = false) {
    val minConfidence: Double
        get() = vertexes?.values?.minOf { it.minConfidence } ?: -999.0
}

@Serializable
class ArchiveBox(
    @SerialName("box") val boxBoundary: Boundary,
    @SerialName("sticker") // TODO: Do we care about knowing which label is the "canonical" one?
    val labelBoundaries: List<Boundary> =
        listOf(), // Also TODO: We may need to make sure this is enumerated.
    // This should also never be empty in practice, labelless boxes are out of scope.
) {
    val keyedBoundaries: Map<String, Boundary> by lazy {
        mapOf(
            "box" to boxBoundary,
            *labelBoundaries
                .mapIndexed { index, boundary -> "label-${index + 1}" to boundary }
                .toTypedArray(),
        )
    }
    val minConfidence: Double by lazy { keyedBoundaries.values.minOf { it.confidence } }
}

@Serializable
class Boundary(val points: Quad, val confidence: Double, val transcription: String? = null)
