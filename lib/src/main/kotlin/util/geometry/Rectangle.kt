package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.Serializable

@Serializable(RectangleSerializer::class)
open class Rectangle protected constructor(override val inner: Array<out Point>) : Quad(inner) {
    init {
        require(isGridAlignedRect()) { "Rectangle has points that do not form a valid rectangle." }
    }

    fun isGridAlignedRect(): Boolean {
        val xMin = inner.minOf { it.x }
        val yMin = inner.minOf { it.y }
        val xMax = inner.maxOf { it.x }
        val yMax = inner.maxOf { it.y }

        return inner.any { it.x == xMin && it.y == yMin } &&
            inner.any { it.x == xMax && it.y == yMin } &&
            inner.any { it.x == xMin && it.y == yMax } &&
            inner.any { it.x == xMax && it.y == yMax }
    }

    companion object {
        fun create(points: Array<out Point>): Rectangle = Rectangle(points)

        fun create(points: List<Point>): Rectangle = Rectangle(points.toTypedArray())
    }
}

object RectangleSerializer : PolySerializer<Rectangle>("Rectangle", Rectangle::create)
