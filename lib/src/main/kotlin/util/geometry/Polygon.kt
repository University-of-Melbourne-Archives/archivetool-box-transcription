package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
open class Polygon protected constructor(open val inner: Array<out Point>) {
    fun bestBoundingBox(): Rectangle {
        val xMin = inner.minOf { it.x }
        val yMin = inner.minOf { it.y }
        val xMax = inner.maxOf { it.x }
        val yMax = inner.maxOf { it.y }

        return Rectangle.create(
            listOf(
                Point.create(xMin, yMin),
                Point.create(xMax, yMin),
                Point.create(xMin, yMax),
                Point.create(xMax, yMax),
            )
        )
    }

    companion object {
        fun create(points: Array<out Point>): Polygon = Polygon(points)
    }
}

abstract class PolySerializer<T : Polygon>(
    val className: String,
    val constructor: (List<Point>) -> T,
) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    private val delegateSerializer = ListSerializer(PointSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor(
            "au.edu.unimelb.archivetool.util.geometry.$className",
            delegateSerializer.descriptor,
        )

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeSerializableValue(delegateSerializer, value.inner.map { it })

    override fun deserialize(decoder: Decoder): T =
        constructor(decoder.decodeSerializableValue(delegateSerializer))
}
