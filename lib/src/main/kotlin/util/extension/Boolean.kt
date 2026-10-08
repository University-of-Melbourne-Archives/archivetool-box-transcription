package au.edu.unimelb.archivetool.util.extension

fun Boolean.orNull(): Boolean? = true.takeIf { this }
