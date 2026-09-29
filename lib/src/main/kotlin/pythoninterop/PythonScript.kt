package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.util.logging.Log
import java.io.BufferedReader
import org.graalvm.polyglot.Value

// A python context in which the file is treated as a "script", being evaluatable at a given time.
class PythonScript(val fileName: String, inject: Set<Injectable> = setOf()) :
    PythonContext(inject) {
    init {
        Log.debug("Lazy-loading python script: $fileName")
    }

    private val scriptContents by lazy {
        BufferedReader({}.javaClass.getResourceAsStream("/python/$fileName")!!.reader()).readText()
    }

    fun eval(): Value {
        Log.debug("Executing python script $fileName")
        return _context.eval("python", scriptContents)
    }
}
