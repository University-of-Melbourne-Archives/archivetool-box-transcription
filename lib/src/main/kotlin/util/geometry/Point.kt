package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bytedeco.opencv.opencv_core.Point as CVPoint

@Serializable(PointSerializer::class)
@JvmInline
value class Point private constructor(val inner: Pair<Int, Int>) {
    val x
        get() = inner.first

    val y
        get() = inner.second

    operator fun plus(other: Point): Point = create(this.x + other.x, this.y + other.y)

    operator fun unaryMinus(): Point = create(-x, -y)

    operator fun minus(other: Point): Point = this + -other

    operator fun component1(): Int = inner.component1()

    operator fun component2(): Int = inner.component2()

    fun toCVPoint(): CVPoint = CVPoint(x, y)

    fun clamp(min: Point, max: Point): Point =
        fromPair(
            min.let { (minX, minY) ->
                max.let { (maxX, maxY) -> x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY) }
            }
        )

    companion object {
        fun create(x: Int, y: Int): Point = Point(Pair(x, y))

        fun fromPair(value: Pair<Int, Int>): Point = Point(value)

        fun Array<out Point>.flatten(): List<Int> =
            this.fold(mutableListOf()) { acc, (x, y) ->
                acc.apply {
                    add(x)
                    add(y)
                }
            }

        fun List<Point>.flatten(): List<Int> =
            this.fold(mutableListOf()) { acc, (x, y) ->
                acc.apply {
                    add(x)
                    add(y)
                }
            }
    }
}

// Surrogate for storing a point in JSON properly.
// https://kotlinlang.org/docs/serialization-create-and-use-serializers.html#serialize-classes-with-a-surrogate-class
@Serializable @SerialName("Point") private class PointSurrogate(val x: Int, val y: Int) {}

object PointSerializer : KSerializer<Point> {
    // The serialNames of descriptors must be unique
    override val descriptor: SerialDescriptor =
        SerialDescriptor(
            "au.edu.unimelb.archivetool.util.geometry.Point",
            PointSurrogate.serializer().descriptor,
        )

    // Converts the original class to the surrogate representation
    override fun serialize(encoder: Encoder, value: Point) {
        val surrogate = PointSurrogate(value.x, value.y)
        encoder.encodeSerializableValue(PointSurrogate.serializer(), surrogate)
    }

    // Converts the surrogate representation back to the original class
    override fun deserialize(decoder: Decoder): Point {
        val surrogate = decoder.decodeSerializableValue(PointSurrogate.serializer())
        return Point.create(surrogate.x, surrogate.y)
    }
}
