package au.edu.unimelb.archivetool.util.wrap

import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.file.Image.FileType

// This was going to wrap calls to runtime supported by Ultralytics YOLO26.
// Unless we port away from Python, this will be a very thin object.
object YOLO {
    val formatRequirements by lazy {
        object : Image.FileType.Supported {
            override val supportedFormats by lazy {
                mapOf(
                    FileType.AVIF to setOf("avif"),
                    FileType.WindowsBitmap to setOf("bmp"),
                    FileType.DigitalNegative to setOf("dng"),
                    FileType.HEIC to setOf("heic"),
                    FileType.HEIF to setOf("heif"),
                    FileType.JPEG2K to setOf("jp2"),
                    FileType.JPEG to setOf("jpeg", "jpg"),
                    FileType.PNG to setOf("png"),
                    FileType.TIFF to setOf("tif", "tiff"),
                    FileType.WebP to setOf("webp"),
                )
            }
        }
    }
}
