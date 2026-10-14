package au.edu.unimelb.archivetool.util.error

import au.edu.unimelb.archivetool.util.logging.Log

fun <T> T?.expect(message: String): T =
    this.unwrap {
        Log.error(message)
        throw RuntimeException(message)
    }
