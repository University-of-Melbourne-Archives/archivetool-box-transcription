package au.edu.unimelb.archivetool.util.geometry

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Rectangle private constructor(val inner: Array<out Point>) {
    init {
        require(inner.size == 4) { "Rectangle had the wrong number of vertices." }
    }

    companion object {
        fun create(vararg points: Point): Rectangle = Rectangle(points)
    }
}
