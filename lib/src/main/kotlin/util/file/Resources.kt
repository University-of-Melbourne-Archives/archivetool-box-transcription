package au.edu.unimelb.archivetool.util.file

import au.edu.unimelb.archivetool.switchboard.interactions.PythonCreatePreviewInteraction.Companion.modelDirectory
import au.edu.unimelb.archivetool.util.extension.asFileAttribute
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

fun copyFromResources(
    resourcePath: String,
    destinationDirectory: Path,
    attributes: FileAttribute<*> = PosixFilePermissions.fromString("rwxrwxr--").asFileAttribute()
): Path {
    val path = destinationDirectory.resolve(resourcePath).createParentDirectories(attributes)

    path.createFile(
        attributes
    ).bufferedWriter().use {
        BufferedReader(InputStreamReader(
            {}.javaClass.getResourceAsStream("/$resourcePath")!!
        )).transferTo(it)
    }

    return path
}