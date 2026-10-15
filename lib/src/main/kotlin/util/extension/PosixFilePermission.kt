package au.edu.unimelb.archivetool.util.extension

import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

fun Set<PosixFilePermission>.asFileAttribute(): FileAttribute<Set<PosixFilePermission>> = PosixFilePermissions.asFileAttribute(this)