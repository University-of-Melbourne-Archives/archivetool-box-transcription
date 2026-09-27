package au.edu.unimelb.archivetool.util.extension

fun <T> T?.isNull(): Boolean = this == null

fun <T> T?.isNotNull(): Boolean = this != null

fun <T, R> T.takeIfNotNull(validator: (T) -> R?): T? = this.takeIf { validator(this) != null }
