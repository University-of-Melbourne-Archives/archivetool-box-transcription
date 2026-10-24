package au.edu.unimelb.archivetool.util.file

import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.extension.asFileAttributes
import au.edu.unimelb.archivetool.util.extension.posixSupported
import au.edu.unimelb.archivetool.util.extension.tryOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

fun copyFromResources(
    resourcePath: String,
    destinationDirectory: Path,
    perms: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwxrwxr--"),
): Path {
    val attributes = perms.asFileAttributes()
    val path = destinationDirectory.resolve(resourcePath).createParentDirectories(*attributes)

    val input =
        {}.javaClass
            .getResourceAsStream("/$resourcePath")
            .expect("Resource not found: /$resourcePath")

    input.use {
        path.tryOrNull<Path, Unit, java.nio.file.FileAlreadyExistsException> {
            createFile(*attributes)
        }
        Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
        if (posixSupported) Files.setPosixFilePermissions(path, perms)
    }

    return path
}
