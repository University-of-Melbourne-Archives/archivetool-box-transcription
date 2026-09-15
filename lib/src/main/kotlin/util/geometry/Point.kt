package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(PointSerializer::class)
@JvmInline
value class Point private constructor(val inner: Pair<Int, Int>) {
    val x
        get() = inner.first

    val y
        get() = inner.second

    companion object {
        fun create(x: Int, y: Int): Point = Point(Pair(x, y))

        fun fromPair(value: Pair<Int, Int>): Point = Point(value)
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
