package au.edu.unimelb.archivetool.pythoninterop.extensions

import org.graalvm.polyglot.Value

operator fun Value.get(index: Long): Value = getArrayElement(index)

operator fun Value.get(index: Int): Value = this[index.toLong()]
