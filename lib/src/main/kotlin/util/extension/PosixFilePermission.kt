package au.edu.unimelb.archivetool.util.extension

import java.nio.file.FileSystems
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/** Whether the default filesystem supports POSIX permissions (false on Windows). */
val posixSupported: Boolean by lazy {
    "posix" in FileSystems.getDefault().supportedFileAttributeViews()
}

/**
 * These permissions as file attributes for `createFile`, `createDirectory` etc. (pass with `*`).
 * Empty where POSIX permissions aren't supported, as passing them there throws.
 */
fun Set<PosixFilePermission>.asFileAttributes(): Array<FileAttribute<*>> =
    if (posixSupported) arrayOf(PosixFilePermissions.asFileAttribute(this)) else emptyArray()
