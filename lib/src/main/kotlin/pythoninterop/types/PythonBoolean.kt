package au.edu.unimelb.archivetool.pythoninterop.types

import au.edu.unimelb.archivetool.pythoninterop.PythonType
import au.edu.unimelb.archivetool.pythoninterop.converters.BooleanConverter
import java.lang.ref.WeakReference
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class PythonBoolean private constructor(context: WeakReference<Context>) :
    PythonType<Boolean>(context) {
    constructor(context: WeakReference<Context>, inPolyglotValue: Value) : this(context) {
        polyglotValue = inPolyglotValue
    }

    constructor(context: WeakReference<Context>, inJvmValue: Boolean) : this(context) {
        jvmValue = inJvmValue
    }

    override val converter = BooleanConverter
}
