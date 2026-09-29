package au.edu.unimelb.archivetool.util.extension

import au.edu.unimelb.archivetool.util.logging.Log

fun <T, R> T.tryOrNull(couldThrow: T.() -> R): R? =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Exception) {
            Log.debug("tryOrNull caught exception: $e")
            null
        }
    }

inline fun <T, reified Ex> T.tryOrIgnore(couldThrow: T.() -> T): T =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Exception) {
            if (e is Ex) {
                Log.debug("tryOrIgnore caught exception: $e")
                return@let it
            }
            throw e
        }
    }

inline fun <T> T.tryOrIgnoreAll(couldThrow: T.() -> T): T =
    this.let {
        try {
            couldThrow(it)
        } catch (e: Exception) {
            Log.debug("tryOrIgnoreAll caught exception: $e")
            return@let it
        }
    }
