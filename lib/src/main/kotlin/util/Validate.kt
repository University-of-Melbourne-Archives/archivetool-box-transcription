package au.edu.unimelb.archivetool.util

fun <T> T.validate(validator: (T) -> Boolean): T? {
    if (!validator(this)) return null

    return this
}
