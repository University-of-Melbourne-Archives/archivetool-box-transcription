package au.edu.unimelb.archivetool.util.extension

fun String.toRegex(vararg options: RegexOption) = toRegex(options.toSet())
