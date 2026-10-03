package au.edu.unimelb.archivetool.util.wrap

import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.HSLAColour
import au.edu.unimelb.archivetool.util.HSVAColour
import au.edu.unimelb.archivetool.util.RGBAColour
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.file.Image.FileType
import au.edu.unimelb.archivetool.util.float01ToByte0255
import io.nacular.doodle.drawing.Color
import io.nacular.doodle.drawing.HslColor
import io.nacular.doodle.drawing.HsvColor
import io.nacular.doodle.drawing.paint
import io.nacular.doodle.drawing.toRgb
import io.nacular.measured.units.Angle

object Doodle {
    sealed interface AnyColor {
        val paint
            get() = toRGB().paint

        fun toRGB(): Color =
            rgb()?.inner
                ?: when (this) {
                    is HSL -> inner.toRgb()
                    is HSV -> inner.toRgb()
                    else -> null!!
                }

        fun toHSL(): HslColor =
            hsl()?.inner
                ?: when (this) {
                    is RGB -> HslColor.Companion(inner)
                    is HSV -> HslColor.Companion(inner.toRgb())
                    else -> null!!
                }

        fun toHSV(): HsvColor =
            hsv()?.inner
                ?: when (this) {
                    is RGB -> HsvColor.Companion(inner)
                    is HSL -> HsvColor.Companion(inner.toRgb())
                    else -> null!!
                }

        fun rgb(): RGB? = this as? RGB

        fun hsl(): HSL? = this as? HSL

        fun hsv(): HSV? = this as? HSV

        data class RGB(val inner: Color) : AnyColor

        data class HSL(val inner: HslColor) : AnyColor

        data class HSV(val inner: HsvColor) : AnyColor

        fun toColour(): Colour =
            when (this) {
                is HSL ->
                    HSLAColour(
                        inner.hue `in` Angle.degrees,
                        float01ToByte0255(inner.saturation),
                        float01ToByte0255(inner.lightness),
                        float01ToByte0255(inner.opacity),
                    )
                is HSV ->
                    HSVAColour(
                        inner.hue `in` Angle.degrees,
                        float01ToByte0255(inner.saturation),
                        float01ToByte0255(inner.value),
                        float01ToByte0255(inner.opacity),
                    )
                is RGB ->
                    RGBAColour(inner.red, inner.green, inner.blue, float01ToByte0255(inner.opacity))
            }

        companion object {
            operator fun invoke(c: Color) = RGB(c)

            operator fun invoke(c: HslColor) = HSL(c)

            operator fun invoke(c: HsvColor) = HSV(c)

            fun Colour.Companion.fromDoodleColor(c: AnyColor): Colour = c.toColour()
        }
    }

    typealias Point = io.nacular.doodle.geometry.Point

    typealias Rectangle = io.nacular.doodle.geometry.Rectangle

    // Doodle uses Skia internally. If we ever depend on Skia directly, this should be deferred to
    // that.
    val formatRequirements by lazy {
        object : Image.FileType.Supported {
            override val supportedFormats by lazy {
                mapOf(
                    FileType.WindowsBitmap to null,
                    FileType.GIF to null,
                    FileType.AVIF to null,
                    FileType.HEIC to null,
                    FileType.HEIF to null,
                    FileType.ICO to null,
                    FileType.JPEG to null,
                    FileType.PNG to null,
                    // SVG. Not loaded here.
                    FileType.WAPBitmap to null,
                    FileType.WebP to null,
                    // Even though not listed, the following also has support according to
                    // https://github.com/google/skia/tree/main/include/codec
                    FileType.JPEGXL to null,
                )
            }
        }
    }
}
