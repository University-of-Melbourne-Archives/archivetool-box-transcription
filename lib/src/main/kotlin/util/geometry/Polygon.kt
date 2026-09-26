package au.edu.unimelb.archivetool.util.geometry

import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.util.map
import kotlin.getValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
open class Polygon protected constructor(val vertices: Array<out Point>) {
    val xMin by lazy { vertices.minOf { it.x } }
    val yMin by lazy { vertices.minOf { it.y } }
    val xMax by lazy { vertices.maxOf { it.x } }
    val yMax by lazy { vertices.maxOf { it.y } }

    val floatArray: FloatArray by lazy {
        vertices
            .map { it.inner }
            .map { it.map(Int::toFloat) }
            .flatMap { it.toList() }
            .toFloatArray()
    }

    open fun replaceIndex(i: Int, p: Point): Polygon =
        create(
            vertices
                .takeIf { i < it.size }
                .expect("Tried to replace an OOB index")
                .mapIndexed { index, point ->
                    if (index == i) {
                        p
                    } else {
                        point
                    }
                }
        )

    open fun mapPoints(mapper: (Point) -> Point): Polygon = create(vertices.map(mapper))

    fun bestBoundingBox(): Rectangle =
        Rectangle.create(
            listOf(
                Point.create(xMin, yMin),
                Point.create(xMax, yMin),
                Point.create(xMin, yMax),
                Point.create(xMax, yMax),
            )
        )

    companion object {
        fun create(points: Array<out Point>): Polygon = Polygon(points)

        fun create(points: List<Point>): Polygon = Polygon(points.toTypedArray())

        fun create(vararg points: Pair<Int, Int>): Polygon =
            create(points.map { Point.fromPair(it) })
    }
}

abstract class PolySerializer<T : Polygon>(className: String, val constructor: (List<Point>) -> T) :
    KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    private val delegateSerializer = ListSerializer(PointSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor(
            "au.edu.unimelb.archivetool.util.geometry.$className",
            delegateSerializer.descriptor,
        )

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeSerializableValue(delegateSerializer, value.vertices.map { it })

    override fun deserialize(decoder: Decoder): T =
        constructor(decoder.decodeSerializableValue(delegateSerializer))
}
