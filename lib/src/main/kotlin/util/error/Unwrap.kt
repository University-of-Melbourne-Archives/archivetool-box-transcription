package au.edu.unimelb.archivetool.util.error

fun <T> T?.unwrap(panic: () -> Nothing): T {
    if (this == null) panic()

    return this
}
