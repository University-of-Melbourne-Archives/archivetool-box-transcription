package au.edu.unimelb.archivetool.util.extension

fun <T> T?.isNull(): Boolean = this == null

fun <T> T?.isNotNull(): Boolean = this != null

fun <T, R> T.takeIfNotNull(validator: (T) -> R?): T? = this.takeIf { validator(this) != null }

fun <T> T.applyIf(condition: (T) -> Boolean, mapper: T.() -> T): T = let {
    val func: (T.() -> T) = if (condition(it))
        mapper
    else ({
        noop(this)
        this
    })

    it.apply {
        func()
    }
}
