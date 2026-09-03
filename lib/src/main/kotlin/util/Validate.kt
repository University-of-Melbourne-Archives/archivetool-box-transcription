package au.edu.unimelb.archivetool.util

// NOTE: This function actually has an exact clone in the standard library I didn't know about, takeUnless.
// I will probably delete this at some stage and just use takeUnless
@Deprecated("Redundant with standard library.",
    ReplaceWith("takeUnless(validator)", imports))
fun <T> T.validate(validator: (T) -> Boolean): T? {
    if (!validator(this)) return null

    return this
}
