package au.edu.unimelb.archivetool.util

import org.bytedeco.opencv.opencv_core.Scalar

sealed interface Colour {
    fun rgb(): RGBColour

    fun rgba(): RGBAColour

    companion object {
        val WHITE = RGBAColour.WHITE
    }
}

@JvmInline
value class RGBColour(val inner: UInt) : Colour {
    constructor(
        r: UByte,
        g: UByte,
        b: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(r, g, b, 0.toUByte()))

    val r
        get() = (inner shr 24) and 0xffu

    val g
        get() = (inner shr 16) and 0xffu

    val b
        get() = (inner shr 8) and 0xffu

    val rB
        get() = (inner and 0xffu).toUByte()

    val gB
        get() = ((inner shr 8) and 0xffu).toUByte()

    val bB
        get() = ((inner shr 16) and 0xffu).toUByte()

    fun toCVBGRScalar(): Scalar = rgba().toCVBGRAScalar()

    override fun rgb(): RGBColour = this

    override fun rgba(): RGBAColour = RGBAColour(this.inner)

    companion object {
        val WHITE = RGBColour(0xffffffffu)
    }
}

@JvmInline
value class RGBAColour(val inner: UInt) : Colour {
    constructor(
        r: UByte,
        g: UByte,
        b: UByte,
        a: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(r, g, b, a))

    val r
        get() = (inner shr 24) and 0xffu

    val g
        get() = (inner shr 16) and 0xffu

    val b
        get() = (inner shr 8) and 0xffu

    val a
        get() = inner and 0xffu

    // Don't ask me why this needs these to be doubles when it's still a 0-255 range
    fun toCVBGRAScalar(): Scalar = Scalar(b.toDouble(), g.toDouble(), r.toDouble(), a.toDouble())

    override fun rgb(): RGBColour = RGBColour(this.inner)

    override fun rgba(): RGBAColour = this

    companion object {
        val WHITE = RGBAColour(0xffffffffu)
    }
}
