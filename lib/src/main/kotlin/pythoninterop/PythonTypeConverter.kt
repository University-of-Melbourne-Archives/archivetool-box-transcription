package au.edu.unimelb.archivetool.pythoninterop

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

// A Polyglot <-> Jvm Type Converter.
// Ideally implement this as either
// - An anonymous object on the `PythonType` implementation if the type is a one-off
// - An object in the `converters` package if it's likely to get reused
interface PythonTypeConverter<T> : JvmToPythonConverter<T>, PythonToJvmConverter<T>

interface PythonToJvmConverter<T> {
    fun toJvmValue(polyglotValue: Value): T
}

interface JvmToPythonConverter<T> {
    fun toPolyglotValue(context: Context, jvmValue: T): Value
}
