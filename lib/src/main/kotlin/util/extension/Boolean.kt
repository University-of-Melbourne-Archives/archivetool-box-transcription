package au.edu.unimelb.archivetool.util.extension

fun Boolean.nullify(): Unit? = Unit.takeIf { this }
