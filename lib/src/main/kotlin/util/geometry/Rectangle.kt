package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.Serializable
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Size

@Serializable(RectangleSerializer::class)
open class Rectangle protected constructor(vertices: Array<out Point>) : Quad(vertices) {
    init {
        require(isGridAlignedRect()) { "Rectangle has points that do not form a valid rectangle." }
    }

    val width by lazy { xMax - xMin }
    val height by lazy { yMax - yMin }

    fun isGridAlignedRect(): Boolean =
        vertices.any { it.x == xMin && it.y == yMin } &&
            vertices.any { it.x == xMax && it.y == yMin } &&
            vertices.any { it.x == xMin && it.y == yMax } &&
            vertices.any { it.x == xMax && it.y == yMax }

    override fun toClockwise(): Rectangle =
        create(xMin to yMin, xMax to yMin, xMax to yMax, xMin to yMax)

    fun intoCVRect(): Rect = Rect(xMin, yMin, width, height)

    fun getCVSize(): Size = Size(width, height)

    companion object {
        fun create(vararg points: Pair<Int, Int>): Rectangle =
            create(points.map { Point.fromPair(it) })

        fun create(points: Array<out Point>): Rectangle = Rectangle(points)

        fun create(points: List<Point>): Rectangle = Rectangle(points.toTypedArray())

        fun create(x: Int, y: Int, w: Int, h: Int): Rectangle =
            create(x to y, (x + w) to y, (x + w) to (y + h), x to (y + h))

        fun fromCVRect(rect: Rect): Rectangle {
            val x = rect.x()
            val y = rect.y()
            val w = rect.width()
            val h = rect.height()
            return create(x, y, w, h)
        }

        fun Rect.toRectangle(): Rectangle = fromCVRect(this)
    }
}

object RectangleSerializer : PolySerializer<Rectangle>("Rectangle", Rectangle::create)
