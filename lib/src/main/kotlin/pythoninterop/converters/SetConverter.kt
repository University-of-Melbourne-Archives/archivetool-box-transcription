package au.edu.unimelb.archivetool.pythoninterop.converters

import au.edu.unimelb.archivetool.pythoninterop.PythonTypeConverter
import au.edu.unimelb.archivetool.pythoninterop.extensions.PolyglotIterable.Companion.asIterable
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

object SetConverter : PythonTypeConverter<Set<Value>> {
    override fun toJvmValue(polyglotValue: Value): Set<Value> = polyglotValue.asIterable().toSet()

    override fun toPolyglotValue(context: Context, jvmValue: Set<Value>): Value {
        val list = jvmValue.toList()
        return context.eval("python", "set").execute(list)
    }
}
