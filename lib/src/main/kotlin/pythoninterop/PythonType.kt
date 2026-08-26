package au.edu.unimelb.archivetool.pythoninterop

import java.lang.ref.WeakReference
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

abstract class PythonType<T> protected constructor(private val context: WeakReference<Context>) {
    // These aren't necessary here but the shape of these is good reference for implementors.
    constructor(context: WeakReference<Context>, inPolyglotValue: Value) : this(context) {
        polyglotValue = inPolyglotValue
    }

    constructor(context: WeakReference<Context>, inJvmValue: T) : this(context) {
        jvmValue = inJvmValue
    }

    // Tagged Union Type of both value types.
    sealed interface OneOfValue<T> {
        class PolyglotValue<T>(val inner: Value) : OneOfValue<T>

        class JvmValue<T>(val inner: T) : OneOfValue<T>
    }

    // Dependency Injected Type Converter
    protected abstract val converter: PythonTypeConverter<T>

    // The most recently set value of either type.
    private lateinit var actualValue: OneOfValue<T>

    // Adapters to retrieve the members as either type of value.
    var polyglotValue: Value
        get() {
            return when (actualValue) {
                is OneOfValue.JvmValue<*> ->
                    converter.toPolyglotValue(
                        context.get()!!,
                        (actualValue as? OneOfValue.JvmValue)!!.inner,
                    )
                is OneOfValue.PolyglotValue<*> -> (actualValue as? OneOfValue.PolyglotValue)!!.inner
            }
        }
        set(value) {
            actualValue = OneOfValue.PolyglotValue(value)
        }

    var jvmValue: T
        get() {
            return when (actualValue) {
                is OneOfValue.JvmValue<*> -> (actualValue as? OneOfValue.JvmValue)!!.inner
                is OneOfValue.PolyglotValue<*> ->
                    converter.toJvmValue((actualValue as? OneOfValue.PolyglotValue)!!.inner)
            }
        }
        set(value) {
            actualValue = OneOfValue.JvmValue(value)
        }
}
