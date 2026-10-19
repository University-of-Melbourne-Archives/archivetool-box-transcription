package au.edu.unimelb.archivetool.util.extension

/** Convert an integer percentage (0-100) to a 0.0-1.0 scale Double. */
fun Int.percentToFraction(): Double = (this).toDouble() / 100.0
