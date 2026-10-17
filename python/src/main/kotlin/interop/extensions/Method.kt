package au.edu.unimelb.archivetool.python.interop.extensions

import org.graalvm.polyglot.Value

operator fun Value.invoke(vararg params: Any): Value = execute(*params)
