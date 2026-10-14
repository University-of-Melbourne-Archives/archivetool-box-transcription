package au.edu.unimelb.archivetool.util.wrap

import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.file.Image.FileType

// This was going to wrap calls to the OpenAI API, but since we do it through Python, it is a very
// thin object.
object OpenAI {
    val formatRequirements by lazy {
        object : Image.FileType.Supported {
            override val supportedFormats by lazy {
                mapOf(
                    FileType.GIF to setOf("gif"),
                    FileType.JPEG to setOf("jpg", "jpeg"),
                    FileType.PNG to setOf("png"),
                    FileType.WebP to setOf("webp"),
                )
            }
        }
    }
}
