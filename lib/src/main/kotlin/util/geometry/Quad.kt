package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.Serializable

@Serializable(QuadSerializer::class)
open class Quad protected constructor(override val inner: Array<out Point>) : Polygon(inner) {
    init {
        require(inner.size == 4) { "Quad had the wrong number of vertices." }
    }

    companion object {
        fun create(vararg points: Pair<Int, Int>): Quad =
            Quad.create(points.map { Point.fromPair(it) })

        fun create(points: Array<out Point>): Quad = Quad(points)

        fun create(points: List<Point>): Quad = Quad(points.toTypedArray())
    }
}

object QuadSerializer : PolySerializer<Quad>("Quad", Quad::create)
