package au.edu.unimelb.archivetool.util

fun Boolean.nullify(): Unit? = Unit.takeIf { this }
