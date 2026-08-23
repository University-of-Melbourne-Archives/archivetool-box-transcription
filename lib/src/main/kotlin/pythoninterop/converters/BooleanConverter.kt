package au.edu.unimelb.archivetool.pythoninterop.converters

import au.edu.unimelb.archivetool.pythoninterop.PythonTypeConverter
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

object BooleanConverter : PythonTypeConverter<Boolean> {
    override fun toJvmValue(polyglotValue: Value): Boolean {
        try {
            return polyglotValue.asBoolean()
        } catch (e: Exception) {
            throw RuntimeException("Tried to convert non-boolean python type to Boolean", e)
        }
    }

    override fun toPolyglotValue(context: Context, jvmValue: Boolean): Value {
        return context.asValue(jvmValue)
    }
}
