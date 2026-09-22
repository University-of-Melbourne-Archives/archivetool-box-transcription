package au.edu.unimelb.archivetool.util

fun <T> T?.isNull(): Boolean = this == null

fun <T> T?.isNotNull(): Boolean = this != null
