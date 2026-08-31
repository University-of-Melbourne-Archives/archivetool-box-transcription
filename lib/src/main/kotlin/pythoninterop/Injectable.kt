package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import java.util.function.Consumer
import org.graalvm.polyglot.Context

interface Injectable {
    abstract fun inject(ctx: Context)
}
