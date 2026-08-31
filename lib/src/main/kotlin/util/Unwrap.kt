package au.edu.unimelb.archivetool.util

fun <T> T?.unwrap(panic: () -> Nothing): T {
    if (this == null) panic()

    return this
}
