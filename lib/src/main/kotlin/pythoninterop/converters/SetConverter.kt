package au.edu.unimelb.archivetool.pythoninterop.converters

import au.edu.unimelb.archivetool.pythoninterop.PythonTypeConverter
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value


object SetConverter: PythonTypeConverter<Set<Value>> {
    override fun toJvmValue(polyglotValue: Value): Set<Value> {
        val set: MutableSet<Value> = mutableSetOf()
        val iterator: Value = polyglotValue.iterator
        while (iterator.hasIteratorNextElement()) {
            set.add(iterator.iteratorNextElement)
        }
        return set
    }

    override fun toPolyglotValue(
        context: Context,
        jvmValue: Set<Value>
    ): Value {
        val list = jvmValue.toList()
        return context.eval("python", "set").execute(list)
    }
}