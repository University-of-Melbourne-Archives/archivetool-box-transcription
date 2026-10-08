package au.edu.unimelb.archivetool.util.geometry

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

    override fun mapPoints(mapper: (Point) -> Point): Quad = create(vertices.map(mapper))

    fun offsetBy(x: Int, y: Int): Quad = create(vertices.map { it + Point.create(x, y) })

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
    }
}

object QuadSerializer : PolySerializer<Quad>("Quad", Quad::create)
