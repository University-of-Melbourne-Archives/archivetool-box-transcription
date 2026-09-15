package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.geometry.Quad
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JobFile(val crops: MutableMap<String, Crop> = mutableMapOf(), val approved: Boolean = false) {
    @Serializable
    sealed class Crop {
        abstract val points: Quad
        abstract val confidence: Double
        abstract val transcription: String?
    }

    @Serializable
    @SerialName("box")
    class BoxCrop(
        override val points: Quad,
        override val transcription: String? = null,
        override val confidence: Double,
    ) : Crop()

    @Serializable
    @SerialName("label")
    class LabelCrop(
        override val points: Quad,
        override val transcription: String? = null,
        override val confidence: Double,
    ) : Crop()
}
