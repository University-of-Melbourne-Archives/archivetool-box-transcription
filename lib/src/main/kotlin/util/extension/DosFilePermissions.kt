package au.edu.unimelb.archivetool.util.extension

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/** Whether the default filesystem supports POSIX permissions (false on Windows). */
val dosSupported: Boolean by lazy {
    "dos" in FileSystems.getDefault().supportedFileAttributeViews()
}

/**
 * Makes a file hidden on Windows. On Unix-likes, prepending is fine.
 */
fun Path.setHidden() {
    if (dosSupported)
        Files.setAttribute(this, "dos:hidden", true);
}