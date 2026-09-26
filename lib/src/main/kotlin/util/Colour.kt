package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.logging.Log.expect
import org.bytedeco.opencv.opencv_core.Scalar
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface Colour {
    fun hsl(): HSLColour
    fun hsla(): HSLAColour

    fun hsv(): HSVColour
    fun hsva(): HSVAColour

    fun rgb(): RGBColour
    fun rgba(): RGBAColour

    companion object {
        val WHITE = RGBAColour.WHITE
    }
}

fun hueDegToByte(deg: Double): UByte = ((deg / 360.0) * 255).roundToInt().toUByte()
fun hueByteToDeg(byte: UByte): Double = (byte.toDouble() * 360.0) / 255.0
fun double01ToUbyte0255(double: Double): UByte = (double * 255).roundToInt().toUByte()
fun ubyte0255ToDouble01(byte: UByte): Double = byte.toDouble() / 255.0
fun ubyte0255ToDouble01(byte: UInt): Double = byte.toDouble() / 255.0

// HSL/HSV, conversion based on the math in https://en.wikipedia.org/wiki/HSL_and_HSV
// Rather than Hue internally being 360 degrees, we store it in a range of 0-255 for byte size reasons.
@JvmInline
value class HSLColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        l: UByte,
    ) : this(@OptIn(ExperimentalUnsignedTypes::class) uBytesToUInt(hueDegToByte(h), s, l, 0.toUByte()))

    val h
        get() = hueByteToDeg(((inner shr 24) and 0xffu).toUByte())

    val s
        get() = (inner shr 16) and 0xffu

    val l
        get() = (inner shr 8) and 0xffu

    fun withAlpha(a: UByte): HSLAColour = HSLAColour(inner and a.toUInt())

    override fun hsl(): HSLColour = this

    override fun hsla(): HSLAColour = withAlpha(255u)

    override fun hsv(): HSVColour {
        val l = ubyte0255ToDouble01(l)
        val s = ubyte0255ToDouble01(s)
        val v = l + (s * min(l, 1.0 - 1))
        val sV = 2.0 * (1.0 - ((l checkedDiv v) ?: 1.0))
        return HSVColour(h, double01ToUbyte0255(sV), double01ToUbyte0255(v))
    }

    override fun hsva(): HSVAColour = hsv().withAlpha(255u)

    override fun rgb(): RGBColour {
        val l = ubyte0255ToDouble01(l)
        val s = ubyte0255ToDouble01(s)
        val chroma = (1 - abs(2 * l - 1)) * s
        val hprime = (h / 60.0)
        val q = chroma * (1 - abs((hprime % 2) - 1))
        val m = l - (chroma / 2)
        val c = chroma + m
        val x = q + m

        val (r, g, b) = when (hprime) {
            in 0.0..1.0 -> Triple(c,x,m)
            in 1.0..2.0 -> Triple(x,c,m)
            in 2.0..3.0 -> Triple(m,c,x)
            in 3.0..4.0 -> Triple(m,x,c)
            in 4.0..5.0 -> Triple(x,m,c)
            in 5.0..6.0 -> Triple(c,m,x)
            else -> null as Triple<Double, Double, Double>?
        }.expect("Invalid Hue.").map {
            (it * 255).roundToInt().toUByte()
        }

        return RGBColour(r, g, b)
    }

    override fun rgba(): RGBAColour = rgb().rgba()
}

@JvmInline
value class HSLAColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        l: UByte,
        a: UByte
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

    override fun rgb(): RGBColour = rgba().rgb()

    override fun rgba(): RGBAColour = hsl().rgb().withAlpha(a.toUByte())
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

    fun withAlpha(a: UByte): HSVAColour = HSVAColour(inner and a.toUInt())

    override fun hsl(): HSLColour {
        val v = ubyte0255ToDouble01(v)
        val s = ubyte0255ToDouble01(s)
        val l = v * (1.0 - (s / 2.0))
        val sL = ((v - l) checkedDiv (min(l, 1 - l))) ?: 0.0

        return HSLColour(h, double01ToUbyte0255(sL), double01ToUbyte0255(l))
    }

    override fun hsla(): HSLAColour = hsl().hsla()

    override fun hsv(): HSVColour = this

    override fun hsva(): HSVAColour = withAlpha(255.toUByte())

    override fun rgb(): RGBColour {
        val v = ubyte0255ToDouble01(v)
        val s = ubyte0255ToDouble01(s)
        val chroma = v * s
        val hprime = (h / 60.0)
        val q = chroma * (1 - abs((hprime % 2) - 1))
        val m = v - chroma
        val c = chroma + m
        val x = q + m

        val (r, g, b) = when (hprime) {
            in 0.0..1.0 -> Triple(c,x,m)
            in 1.0..2.0 -> Triple(x,c,m)
            in 2.0..3.0 -> Triple(m,c,x)
            in 3.0..4.0 -> Triple(m,x,c)
            in 4.0..5.0 -> Triple(x,m,c)
            in 5.0..6.0 -> Triple(c,m,x)
            else -> null as Triple<Double, Double, Double>?
        }.expect("Invalid Hue.").map {
            (it * 255).roundToInt().toUByte()
        }

        return RGBColour(r, g, b)
    }

    override fun rgba(): RGBAColour = rgb().withAlpha(255u)
}

@JvmInline
value class HSVAColour(val inner: UInt) : Colour {
    constructor(
        h: Double,
        s: UByte,
        v: UByte,
        a: UByte
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

    override fun rgb(): RGBColour = hsv().rgb()

    override fun rgba(): RGBAColour = rgb().withAlpha(a.toUByte())
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

    fun withAlpha(a: UByte): RGBAColour = RGBAColour(inner and a.toUInt())

    fun toCVBGRScalar(): Scalar = rgba().toCVBGRAScalar()

    override fun hsl(): HSLColour = withAlpha(255u).hsl()

    override fun hsla(): HSLAColour = withAlpha(255u).hsla()

    override fun hsv(): HSVColour = withAlpha(255u).hsv()

    override fun hsva(): HSVAColour = withAlpha(255u).hsva()

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
    override fun hsl(): HSLColour = hsla().hsl()

    override fun hsla(): HSLAColour = hsva().hsla()

    override fun hsv(): HSVColour = hsva().hsv()

    override fun hsva(): HSVAColour {
        val rD = ubyte0255ToDouble01(r)
        val gD = ubyte0255ToDouble01(g)
        val bD = ubyte0255ToDouble01(b)
        // Our value is our maximum channel
        val v = ubyte0255ToDouble01(maxOf(r, g, b))
        val min = ubyte0255ToDouble01(minOf(r, g, b))

        val chroma = v - min

        val s = (chroma checkedDiv v)?.let(::double01ToUbyte0255) ?: 0u

        val h = (when (v) {
            rD -> ((gD - bD) checkedDiv chroma)?.rem(6.0)
            gD -> ((bD - rD) checkedDiv chroma)?.plus(2)
            bD -> ((rD - gD) checkedDiv chroma)?.plus(4)
            else -> null!!
        } ?: 0.0) * 60.0

        return HSVAColour(h, s, double01ToUbyte0255(v), a.toUByte())
    }

    override fun rgb(): RGBColour = RGBColour(this.inner)

    override fun rgba(): RGBAColour = this

    companion object {
        val WHITE = RGBAColour(0xffffffffu)
    }
}
