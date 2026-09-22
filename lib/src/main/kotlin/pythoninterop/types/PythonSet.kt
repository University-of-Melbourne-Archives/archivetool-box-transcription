package au.edu.unimelb.archivetool.pythoninterop.types

import au.edu.unimelb.archivetool.pythoninterop.PythonType
import au.edu.unimelb.archivetool.pythoninterop.PythonTypeConverter
import au.edu.unimelb.archivetool.pythoninterop.converters.BooleanConverter
import au.edu.unimelb.archivetool.pythoninterop.converters.SetConverter
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.lang.ref.WeakReference

class PythonSet<J, P: PythonTypeConverter<J>> private constructor(context: WeakReference<Context>, val innerConverter: P) :
    PythonType<Set<J>>(context) {
    constructor(context: WeakReference<Context>, inPolyglotValue: Value, innerConverter: P) : this(context, innerConverter) {
        polyglotValue = inPolyglotValue
    }

    constructor(context: WeakReference<Context>, inJvmValue: Set<J>, innerConverter: P) : this(context, innerConverter) {
        jvmValue = inJvmValue
    }

    override val converter = object : PythonTypeConverter<Set<J>> {
        override fun toJvmValue(polyglotValue: Value): Set<J> =
            SetConverter.toJvmValue(polyglotValue).map {
                innerConverter.toJvmValue(it)
            }.toSet()

        override fun toPolyglotValue(context: Context, jvmValue: Set<J>): Value =
            SetConverter.toPolyglotValue(context,
                jvmValue.map {
                    innerConverter.toPolyglotValue(context, it)
                }.toSet()
            )
    }
}