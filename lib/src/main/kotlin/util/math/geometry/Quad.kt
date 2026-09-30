package au.edu.unimelb.archivetool.util.math.geometry

import kotlin.math.atan2
import kotlinx.serialization.Serializable

@Serializable(QuadSerializer::class)
open class Quad protected constructor(vertices: Array<out Point>) : Polygon(vertices) {
    init {
        require(vertices.size == 4) { "Quad had the wrong number of vertices." }
    }

    open fun toClockwise(): Quad {
        val center = Point.create((xMin + xMax) / 2, (yMin + yMax) / 2)

        return create(
            vertices.sortedBy { vx ->
                (vx - center).let { atan2(it.y.toDouble(), it.x.toDouble()) }
            }
        )
    }

    fun containsPoint(p: Point): Boolean {
        var isInside = false

        val quad = vertices

        var j = quad.size - 1

        for (i in quad.indices) {
            val pi = quad[i]
            val pj = quad[j]
            if ((pi.y > p.y) != (pj.y > p.y)) {
                val xIntersect =
                    pj.x.toDouble() +
                        (p.y - pj.y).toDouble() / (pi.y - pj.y).toDouble() *
                            (pi.x - pj.x).toDouble()
                if (p.x.toDouble() < xIntersect) {
                    isInside = !isInside
                }
            }

            j = i
        }

        return isInside
    }

    override fun replaceIndex(i: Int, p: Point): Quad = upcast(super.replaceIndex(i, p))

    override fun mapPoints(mapper: (Point) -> Point): Quad = create(vertices.map(mapper))

    override fun offsetBy(p: Point): Quad = create(vertices.map { it + p })

    fun confinedTo(outer: Rectangle): Quad =
        create(
            vertices.map {
                it.clamp(
                    Point.fromPair(outer.xMin to outer.yMin),
                    Point.fromPair(outer.xMax to outer.yMax),
                )
            }
        )

    companion object {
        fun create(vararg points: Pair<Int, Int>): Quad = create(points.map { Point.fromPair(it) })

        fun create(points: Array<out Point>): Quad = Quad(points)

        fun create(points: List<Point>): Quad = Quad(points.toTypedArray())

        fun upcast(poly: Polygon): Quad = Quad(poly.vertices)
    }
}

object QuadSerializer : PolySerializer<Quad>("Quad", Quad::create)
