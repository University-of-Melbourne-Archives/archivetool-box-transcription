package au.edu.unimelb.archivetool.util

import io.nacular.doodle.drawing.Color as DoodleColor
import io.nacular.doodle.drawing.HslColor as DoodleHSLColor
import io.nacular.doodle.drawing.HsvColor as DoodleHSVColor
import io.nacular.doodle.drawing.toRgb
import io.nacular.measured.units.Angle.Companion.degrees

object Doodle {
    sealed interface AnyColor {
        fun toRGB(): DoodleColor =
            rgb()?.inner
                ?: when (this) {
                    is HSL -> inner.toRgb()
                    is HSV -> inner.toRgb()
                    else -> null!!
                }

        fun toHSL(): DoodleHSLColor =
            hsl()?.inner
                ?: when (this) {
                    is RGB -> DoodleHSLColor(inner)
                    is HSV -> DoodleHSLColor(inner.toRgb())
                    else -> null!!
                }

        fun toHSV(): DoodleHSVColor =
            hsv()?.inner
                ?: when (this) {
                    is RGB -> DoodleHSVColor(inner)
                    is HSL -> DoodleHSVColor(inner.toRgb())
                    else -> null!!
                }

        fun rgb(): RGB? = this as? RGB

        fun hsl(): HSL? = this as? HSL

        fun hsv(): HSV? = this as? HSV

        data class RGB(val inner: DoodleColor) : AnyColor

        data class HSL(val inner: DoodleHSLColor) : AnyColor

        data class HSV(val inner: DoodleHSVColor) : AnyColor

        fun toColour(): Colour =
            when (this) {
                is HSL ->
                    HSLAColour(
                        inner.hue `in` degrees,
                        float01ToByte0255(inner.saturation),
                        float01ToByte0255(inner.lightness),
                        float01ToByte0255(inner.opacity),
                    )
                is HSV ->
                    HSVAColour(
                        inner.hue `in` degrees,
                        float01ToByte0255(inner.saturation),
                        float01ToByte0255(inner.value),
                        float01ToByte0255(inner.opacity),
                    )
                is RGB ->
                    RGBAColour(
                        inner.red,
                        inner.green,
                        inner.blue,
                        float01ToByte0255(inner.opacity),
                    )
            }

        companion object {
            operator fun invoke(c: DoodleColor) = RGB(c)

            operator fun invoke(c: DoodleHSLColor) = HSL(c)

            operator fun invoke(c: DoodleHSVColor) = HSV(c)

            fun Colour.Companion.fromDoodleColor(c: Doodle.AnyColor): Colour =
                c.toColour()
        }
    }

    typealias Rectangle = io.nacular.doodle.geometry.Rectangle
}
