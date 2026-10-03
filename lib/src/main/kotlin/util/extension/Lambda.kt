package au.edu.unimelb.archivetool.util.extension

fun <T> noop(it: T): T = it

fun <A, R> (() -> R).withIgnoredParameter(): ((A) -> R) = { _ -> this() }

fun <A, B, R> (() -> R).withIgnoredParameters(): ((A, B) -> R) = { _, _ -> this() }

fun <A, B, C, R> (() -> R).with3IgnoredParameters(): ((A, B, C) -> R) = { _, _, _ -> this() }

fun <A, B, C, D, R> (() -> R).with4IgnoredParameters(): ((A, B, C, D) -> R) = { _, _, _, _ ->
    this()
}

fun <T: Any, R> receiverAsParam(func: T.() -> R): (T) -> R = func.asParam()

fun <T: Any, R> (T.() -> R).asParam(): (T) -> R = {
    it.this()
}

fun <T: Any, R> paramAsReceiver(func: (T?) -> R): T.() -> R = {
    func(this)
}

fun <T: Any, R> ((T?) -> R).asReceiver(): T.() -> R = paramAsReceiver(this)