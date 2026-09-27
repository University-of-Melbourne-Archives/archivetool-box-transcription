package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.logging.Log.expect
import io.nacular.doodle.drawing.Color as DoodleColor
import io.nacular.doodle.drawing.HslColor as DoodleHSLColor
import io.nacular.doodle.drawing.HsvColor as DoodleHSVColor
import io.nacular.measured.units.Angle.Companion.degrees
import io.nacular.measured.units.Measure
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import org.bytedeco.opencv.opencv_core.Scalar


sealed interface Colour {
    fun hsl(): HSLColour
    fun hsla(): HSLAColour

    fun hsv(): HSVColour
    fun hsva(): HSVAColour

    fun mono(): MonoColour
    fun monoa(): MonoAColour

    fun rgb(): RGBColour
    fun rgba(): RGBAColour

    fun withAlpha(a: UByte): Colour

    fun toDoodle(): Doodle.AnyColor

    companion object {
        val WHITE = RGBAColour.WHITE
        val LIGHT_GREY = RGBAColour.LIGHT_GREY
        val BLUE = HSLAColour.BLUE
        val CYAN = RGBAColour.CYAN
    }
}

fun hueDegToByte(deg: Double): UByte = ((deg / 360.0) * 255).roundToInt().toUByte()

fun hueByteToDeg(byte: UByte): Double = (byte.toDouble() * 360.0) / 255.0

fun float01ToByte0255(float: Float): UByte = (float * 255).roundToInt().toUByte()

fun float01ToByte0255(double: Double): UByte = (double * 255).roundToInt().toUByte()

fun byte0255ToFloat01(byte: UByte): Double = byte.toDouble() / 255.0

fun byte0255ToFloat01(int: UInt): Double = int.toDouble() / 255.0

// HSL/HSV, conversion based on the math in https://en.wikipedia.org/wiki/HSL_and_HSV
// Rather than Hue internally being 360 degrees, we store it in a range of 0-255 for byte size
// reasons.
@JvmInline
value class HSLColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        l: UByte,
    ) : this(
        @OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(hueDegToByte(h), s, l, 0.toUByte())
    )

    val h
        get() = hueByteToDeg(((inner shr 24) and 0xffu).toUByte())

    val s
        get() = (inner shr 16) and 0xffu

    val l
        get() = (inner shr 8) and 0xffu

    override fun withAlpha(a: UByte): HSLAColour = HSLAColour(inner and a.toUInt())

    override fun hsl(): HSLColour = this

    override fun hsla(): HSLAColour = withAlpha(255u)

    override fun hsv(): HSVColour {
        val l = byte0255ToFloat01(l)
        val s = byte0255ToFloat01(s)
        val v = l + (s * min(l, 1.0 - 1))
        val sV = 2.0 * (1.0 - ((l checkedDiv v) ?: 1.0))
        return HSVColour(h, float01ToByte0255(sV), float01ToByte0255(v))
    }

    override fun hsva(): HSVAColour = hsv().withAlpha(255u)

    override fun mono(): MonoColour = MonoColour(l.toUByte())

    override fun monoa(): MonoAColour = mono().monoa()

    override fun rgb(): RGBColour {
        val l = byte0255ToFloat01(l)
        val s = byte0255ToFloat01(s)
        val chroma = (1 - abs(2 * l - 1)) * s
        val hprime = (h / 60.0)
        val q = chroma * (1 - abs((hprime % 2) - 1))
        val m = l - (chroma / 2)
        val c = chroma + m
        val x = q + m

        val (r, g, b) =
            when (hprime) {
                    in 0.0..1.0 -> Triple(c, x, m)
                    in 1.0..2.0 -> Triple(x, c, m)
                    in 2.0..3.0 -> Triple(m, c, x)
                    in 3.0..4.0 -> Triple(m, x, c)
                    in 4.0..5.0 -> Triple(x, m, c)
                    in 5.0..6.0 -> Triple(c, m, x)
                    else -> null as Triple<Double, Double, Double>?
                }
                .expect("Invalid Hue.")
                .map { (it * 255).roundToInt().toUByte() }

        return RGBColour(r, g, b)
    }

    override fun rgba(): RGBAColour = rgb().rgba()

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.HSL(
            DoodleHSLColor(
                Measure(h, degrees),
                byte0255ToFloat01(s).toFloat(),
                byte0255ToFloat01(l).toFloat(),
            )
        )
    companion object {
        val BLUE = HSLColour(240.0, 255u, 127u)
    }
}

@JvmInline
value class HSLAColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        l: UByte,
        a: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(hueDegToByte(h), s, l, a))

    val h
        get() = hueByteToDeg(((inner shr 24) and 0xffu).toUByte())

    val s
        get() = (inner shr 16) and 0xffu

    val l
        get() = (inner shr 8) and 0xffu

    val a
        get() = inner and 0xffu

    override fun hsl(): HSLColour = HSLColour(inner)

    override fun hsla(): HSLAColour = this

    override fun hsv(): HSVColour = hsl().hsv()

    override fun hsva(): HSVAColour = hsl().hsv().withAlpha(a.toUByte())

    override fun mono(): MonoColour = MonoColour(l.toUByte())

    override fun monoa(): MonoAColour = MonoAColour(l.toUByte(), a.toUByte())

    override fun rgb(): RGBColour = rgba().rgb()

    override fun rgba(): RGBAColour = hsl().rgb().withAlpha(a.toUByte())

    override fun withAlpha(a: UByte): HSLAColour = HSLAColour((inner xor (inner and 0xffu)) and a.toUInt())

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.HSL(
            DoodleHSLColor(
                Measure(h, degrees),
                byte0255ToFloat01(s).toFloat(),
                byte0255ToFloat01(l).toFloat(),
                byte0255ToFloat01(a).toFloat(),
            )
        )
    companion object {
        val BLUE = HSLAColour(240.0, 255u, 127u, 255u)
    }
}

@JvmInline
value class HSVColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        v: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(hueDegToByte(h), s, v))

    val h
        get() = hueByteToDeg(((inner shr 24) and 0xffu).toUByte())

    val s
        get() = (inner shr 16) and 0xffu

    val v
        get() = (inner shr 8) and 0xffu

    override fun withAlpha(a: UByte): HSVAColour = HSVAColour(inner and a.toUInt())

    override fun hsl(): HSLColour {
        val v = byte0255ToFloat01(v)
        val s = byte0255ToFloat01(s)
        val l = v * (1.0 - (s / 2.0))
        val sL = ((v - l) checkedDiv (min(l, 1 - l))) ?: 0.0

        return HSLColour(h, float01ToByte0255(sL), float01ToByte0255(l))
    }

    override fun hsla(): HSLAColour = hsl().hsla()

    override fun hsv(): HSVColour = this

    override fun hsva(): HSVAColour = withAlpha(255.toUByte())
    override fun mono(): MonoColour = MonoColour(v.toUByte())

    override fun monoa(): MonoAColour = mono().monoa()

    override fun rgb(): RGBColour {
        val v = byte0255ToFloat01(v)
        val s = byte0255ToFloat01(s)
        val chroma = v * s
        val hprime = (h / 60.0)
        val q = chroma * (1 - abs((hprime % 2) - 1))
        val m = v - chroma
        val c = chroma + m
        val x = q + m

        val (r, g, b) =
            when (hprime) {
                    in 0.0..1.0 -> Triple(c, x, m)
                    in 1.0..2.0 -> Triple(x, c, m)
                    in 2.0..3.0 -> Triple(m, c, x)
                    in 3.0..4.0 -> Triple(m, x, c)
                    in 4.0..5.0 -> Triple(x, m, c)
                    in 5.0..6.0 -> Triple(c, m, x)
                    else -> null as Triple<Double, Double, Double>?
                }
                .expect("Invalid Hue.")
                .map { (it * 255).roundToInt().toUByte() }

        return RGBColour(r, g, b)
    }

    override fun rgba(): RGBAColour = rgb().withAlpha(255u)

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.HSV(
            DoodleHSVColor(
                Measure(h, degrees),
                byte0255ToFloat01(s).toFloat(),
                byte0255ToFloat01(v).toFloat(),
            )
        )
}

@JvmInline
value class HSVAColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        v: UByte,
        a: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(hueDegToByte(h), s, v, a))

    val h
        get() = hueByteToDeg(((inner shr 24) and 0xffu).toUByte())

    val s
        get() = (inner shr 16) and 0xffu

    val v
        get() = (inner shr 8) and 0xffu

    val a
        get() = inner and 0xffu

    override fun hsl(): HSLColour = hsv().hsl()

    override fun hsla(): HSLAColour = hsv().hsl().withAlpha(a.toUByte())

    override fun hsv(): HSVColour = HSVColour(this.inner)

    override fun hsva(): HSVAColour = this

    override fun mono(): MonoColour = MonoColour(v.toUByte())

    override fun monoa(): MonoAColour = MonoAColour(v.toUByte(), a.toUByte())

    override fun rgb(): RGBColour = hsv().rgb()

    override fun rgba(): RGBAColour = rgb().withAlpha(a.toUByte())

    override fun withAlpha(a: UByte): HSVAColour = HSVAColour((inner xor (inner and 0xffu)) and a.toUInt())

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.HSV(
            DoodleHSVColor(
                Measure(h, degrees),
                byte0255ToFloat01(s).toFloat(),
                byte0255ToFloat01(v).toFloat(),
                byte0255ToFloat01(a).toFloat(),
            )
        )
}
@JvmInline
value class MonoColour(val v: UByte) : Colour {
    override fun hsl(): HSLColour = hsv().hsl()

    override fun hsla(): HSLAColour = hsva().hsla()

    override fun hsv(): HSVColour = HSVColour(0.0, 0u, v)

    override fun hsva(): HSVAColour = hsv().hsva()
    override fun mono(): MonoColour = this

    override fun monoa(): MonoAColour = withAlpha(255u)

    override fun rgb(): RGBColour = RGBColour(v, v, v)

    override fun rgba(): RGBAColour = rgb().rgba()

    override fun withAlpha(a: UByte): MonoAColour = MonoAColour(v, a)

    override fun toDoodle(): Doodle.AnyColor {
        TODO("Not yet implemented")
    }
}

@JvmInline
value class MonoAColour(val inner: UShort) : Colour {
    constructor(
        v: UByte,
        a: UByte
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUShort(v, a))

    val v
        get() = (inner shr 8) and 0xffu

    val a
        get() = 0xffu

    override fun hsl(): HSLColour = hsv().hsl()

    override fun hsla(): HSLAColour = hsva().hsla()

    override fun hsv(): HSVColour =
        HSVColour(0.0, 0u, v.toUByte())

    override fun hsva(): HSVAColour =
        HSVAColour(0.0, 0u, v.toUByte(), a.toUByte())

    override fun mono(): MonoColour = MonoColour(v.toUByte())

    override fun monoa(): MonoAColour = this

    override fun rgb(): RGBColour = RGBColour(v.toUByte(), v.toUByte(), v.toUByte())

    override fun rgba(): RGBAColour = RGBAColour(v.toUByte(), v.toUByte(), v.toUByte(), a.toUByte())

    override fun withAlpha(a: UByte): Colour = MonoAColour(inner xor (inner and 0xffu) and a.toUShort())

    override fun toDoodle(): Doodle.AnyColor = rgba().toDoodle()
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

    override fun withAlpha(a: UByte): RGBAColour = RGBAColour((inner xor (inner and 0xffu)) and a.toUInt())

    fun toCVBGRScalar(): Scalar = rgba().toCVBGRAScalar()

    override fun hsl(): HSLColour = withAlpha(255u).hsl()

    override fun hsla(): HSLAColour = withAlpha(255u).hsla()

    override fun hsv(): HSVColour = withAlpha(255u).hsv()

    override fun hsva(): HSVAColour = withAlpha(255u).hsva()

    override fun mono(): MonoColour = MonoColour((r+g+b / 3u).toUByte())

    override fun monoa(): MonoAColour = mono().monoa()

    override fun rgb(): RGBColour = this

    override fun rgba(): RGBAColour = RGBAColour(this.inner)

    companion object {
        val WHITE = RGBColour(0xffffffffu)
        val LIGHT_GREY = RGBAColour(0xd3d3d3u)
        val CYAN = RGBAColour(0x09d0efffu)
    }

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.RGB(DoodleColor(r.toUByte(), g.toUByte(), b.toUByte()))
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

    override fun hsl(): HSLColour = hsla().hsl()

    override fun hsla(): HSLAColour = hsva().hsla()

    override fun hsv(): HSVColour = hsva().hsv()

    override fun hsva(): HSVAColour {
        val rD = byte0255ToFloat01(r)
        val gD = byte0255ToFloat01(g)
        val bD = byte0255ToFloat01(b)
        // Our value is our maximum channel
        val v = byte0255ToFloat01(maxOf(r, g, b))
        val min = byte0255ToFloat01(minOf(r, g, b))

        val chroma = v - min

        val s = (chroma checkedDiv v)?.let(::float01ToByte0255) ?: 0u

        val h =
            (when (v) {
                rD -> ((gD - bD) checkedDiv chroma)?.rem(6.0)
                gD -> ((bD - rD) checkedDiv chroma)?.plus(2)
                bD -> ((rD - gD) checkedDiv chroma)?.plus(4)
                else -> null!!
            } ?: 0.0) * 60.0

        return HSVAColour(h, s, float01ToByte0255(v), a.toUByte())
    }

    override fun mono(): MonoColour = MonoColour((r+g+b / 3u).toUByte())

    override fun monoa(): MonoAColour = MonoAColour((r+g+b / 3u).toUByte(), a.toUByte())

    override fun rgb(): RGBColour = RGBColour(this.inner)

    override fun rgba(): RGBAColour = this

    override fun withAlpha(a: UByte): RGBAColour = RGBAColour((inner xor (inner and 0xffu)) and a.toUInt())

    override fun toDoodle(): Doodle.AnyColor =
        Doodle.AnyColor.RGB(
            DoodleColor(r.toUByte(), g.toUByte(), b.toUByte(), byte0255ToFloat01(a).toFloat())
        )

    companion object {
        val WHITE = RGBAColour(0xffffffffu)
        val LIGHT_GREY = RGBAColour(0xd3d3d3ffu)
        val CYAN = RGBAColour(0x09d0efffu)
    }
}
