package au.edu.unimelb.archivetool.util.file

import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.extension.isNull
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.wrap.CV
import au.edu.unimelb.archivetool.util.wrap.Doodle
import au.edu.unimelb.archivetool.util.wrap.OpenAI
import au.edu.unimelb.archivetool.util.wrap.YOLO
import java.net.URLConnection
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension

// TODO: I would like type-safe MIME typing, but I couldn't find a library for it.

object Image {
    enum class FileType(val extensions: Set<String>, val mimes: Set<String>) {
        // NOTE: Entries should be ordered in general preference order.
        // This order is based vaguely on quality/size ratio and format convenience.
        // Also ignores animated-only formats and SVG, and some RAW types, where it is unclear if
        // one will be supported.

        // HEIF Variants: We pick the ones we might
        AVIF("avif", mime = "image/avif"),
        HEIC("heic", mime = "image/heic"),
        HEIF("heif", mime = "image/heif"),

        // WebP
        WebP("webp", mime = "image/webp"),

        // PNG
        PNG("png", mime = "image/png"),

        // Modern RAWs and other Proprietary.
        OpenEXR("exr", mime = "image/x-exr"),
        DigitalNegative(
            "dng",
            mimes = setOf("image/x-adobe-dng", "image/x-raw-adobe", "image/dng"),
        ),
        RadianceHDR("hdr", "pic", mime = "image/vnd.radiance"),

        // JPEG-Likes
        JPEGXL("jxl", mime = "image/jxl"),
        JPEG2K("jp2", "jpf", "jpx", mimes = setOf("image/jp2", "image/jpx", "image/jpm")),
        JPEG("jpg", "jpeg", mime = "image/jpeg"),

        // Old Lossy Options
        TIFF("tif", "tiff", mime = "image/tiff"),
        GIF("gif", mime = "image/gif"),

        // Bitmaps
        WindowsBitmap("bmp", mime = "mime"),
        WAPBitmap("wbmp", mime = "image/vnd.wap.wbmp"),
        PNM("pbm", "pgm", "ppm", "pxm", "pnm", mime = "image/x-portable-anymap"),
        ICO("ico", "cur", mimes = setOf("image/x-icon", "image/vnd.microsoft.icon")),
        SunRaster("sr", "sun", "ras", mime = "image/x-sun-raster"),

        // GDAL is a wrapped type but designed for map software.
        // It doesn't have a MIME type.
        GeospatialDAL("gdal", mime = "image/x-none");

        constructor(vararg extensions: String, mime: String) : this(extensions.toSet(), setOf(mime))

        constructor(vararg extensions: String, mimes: Set<String>) : this(extensions.toSet(), mimes)

        fun matchesExtension(ext: String): Boolean = extensions.contains(ext)

        fun matchesMIME(mime: String): Boolean = mimes.contains(mime)

        interface Supported {
            val supportedFormats: Map<FileType, Set<String>?>

            fun supports(path: JobRelativePath): Boolean {
                val type = detect(path) ?: return false
                if (!supportedFormats.containsKey(type.first)) return false
                val supported = supportedFormats[type.first] ?: return true
                return supported.contains(path.relative.extension)
            }

            fun trySupport(path: JobRelativePath) = trySupport(path, this)

            infix fun and(other: Supported): Supported =
                object : Supported {
                    override val supportedFormats: Map<FileType, Set<String>?>
                        get() =
                            this@Supported.supportedFormats.keys
                                .union(other.supportedFormats.keys)
                                .map { k ->
                                    val thisReqs = this@Supported.supportedFormats[k]
                                    val otherReqs = other.supportedFormats[k]

                                    k to (thisReqs?.intersect(otherReqs ?: thisReqs) ?: otherReqs)
                                }
                                .toMap()
                }
        }

        companion object {
            // Boolean matches if it was detectable by file extension.
            fun detect(path: JobRelativePath): Pair<FileType, Boolean>? {
                return entries.run {
                    firstOrNull { it.matchesExtension(path.relative.extension) }
                        ?.let {
                            it.also {
                                Log.trace("Image $path was detected to be of type $it by filename.")
                            } to true
                        }
                        ?: mapNotNull {
                                // TODO: I need to check and see if this actually detects the magic
                                // of modern formats.
                                it to
                                    URLConnection.guessContentTypeFromStream(
                                            path.absolute.inputStream().buffered()
                                        )
                                        ?.lowercase()
                            }
                            .firstOrNull { (variant, mime) ->
                                Log.trace("Checking if MIME type matches $mime")
                                variant.matchesMIME(mime ?: "not-a-real/mime-type")
                            }
                            ?.let { (variant, mime) ->
                                variant.also {
                                    Log.trace(
                                        "Image $path was detected to be of type $it by MIME type $mime."
                                    )
                                } to false
                            }
                            .also {
                                if (it.isNull()) Log.debug("Could not detect a file type for $path")
                            }
                }
            }

            fun trySupport(path: JobRelativePath, supported: Supported): JobRelativePath? {
                if (supported.supports(path)) return path

                assert(supported.supportedFormats.containsKey(bestSupported.first)) {
                    "Image File Support call did not support the best commonly supported format."
                }

                return tryConvert(path, bestSupported).also {
                    if (it.isNull()) Log.warn("Could not convert $path to a supported format.")
                }
            }

            val supportsAll by lazy {
                object : Supported {
                    override val supportedFormats: Map<FileType, Set<String>?> =
                        entries.associateWith {
                            null
                        }
                }
            }

            val allRequirements by lazy {
                (CV.formatRequirements and
                    Doodle.formatRequirements and
                    OpenAI.formatRequirements and
                    YOLO.formatRequirements)
            }

            val bestSupported by lazy {
                entries
                    .first { allRequirements.supportedFormats.keys.contains(it) }
                    .let { it to allRequirements.supportedFormats[it] }
            }
        }
    }

    fun tryConvert(path: JobRelativePath, target: Pair<FileType, Set<String>?>): JobRelativePath? {
        Log.debug("Attempting to convert $path to $target.")

        if (FileType.detect(path)?.first == target.first) {
            val destination =
                Path(GENERATED_PATH)
                    .resolve("conv")
                    .resolve(
                        path.relative.let {
                            it.resolveSibling(
                                "${it.nameWithoutExtension}.${target.second?.firstOrNull() ?: target.first.extensions.first()}"
                            )
                        }
                    )
            path.relative.takeUnless { it.exists() }?.copyTo(destination)

            return JobRelativePath(path.rootDirectory, destination)
        }

        try {
            return target.second?.firstOrNull()?.let { CV.convert(path, it) }
                ?: CV.convert(path, target.first)
        } catch (e: Exception) {
            Log.warn("Image Conversion with OpenCV failed with $e.")
        }
        // TODO: Add Fallback Options (FFmpeg?)
        Log.error("Could not convert file $path to format $target")
        return null
    }
}
