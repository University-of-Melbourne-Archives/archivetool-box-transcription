package au.edu.unimelb.archivetool.switchboard.jobstate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JobFile {
    val crops: MutableMap<String, Crop> = mutableMapOf()
    val approved: Boolean = false

    @Serializable
    sealed class Crop {
        abstract val points: Array<Point>
        abstract val transcription: String?

        init {
            // We must have a full Rectangle.
            require(points.size == 4)
        }

        typealias Point = Pair<Int, Int>

        val Point.x by Point::first
        val Point.y by Point::second
    }

    @Serializable
    @SerialName("box")
    class BoxCrop(override val points: Array<Point>, override val transcription: String? = null) :
        Crop()

    @Serializable
    @SerialName("label")
    class LabelCrop(override val points: Array<Point>, override val transcription: String? = null) :
        Crop()
}
