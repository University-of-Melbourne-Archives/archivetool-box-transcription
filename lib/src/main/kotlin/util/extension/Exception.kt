package au.edu.unimelb.archivetool.util.extension

import au.edu.unimelb.archivetool.util.logging.Log

inline fun <T, R, reified Ex : Exception> T.tryOrNull(couldThrow: T.() -> R): R? =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Ex) {
            Log.debug("tryOrNull caught exception: $e")
            null
        }
    }

fun <T, R> T.tryOrNullAll(couldThrow: T.() -> R): R? =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Exception) {
            Log.debug("tryOrNull caught exception: $e")
            null
        }
    }

inline fun <T, reified Ex : Exception> T.tryOrIgnore(couldThrow: T.() -> T): T =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Ex) {
            Log.debug("tryOrIgnore caught exception: $e")
            return@let it
        }
    }

fun <T> T.tryOrIgnoreAll(couldThrow: T.() -> T): T =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Exception) {
            Log.debug("tryOrIgnoreAll caught exception: $e")
            return@let it
        }
    }
