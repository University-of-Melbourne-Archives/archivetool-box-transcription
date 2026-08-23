package au.edu.unimelb.archivetool.pythoninterop

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import org.graalvm.polyglot.Context
import java.util.function.Consumer

sealed interface Injectable {
    abstract fun inject(ctx: Context)

    class Logger : Injectable {
        override fun inject(ctx: Context) {
            ctx.getBindings("python").putMember("Log", this)
        }

        @JvmField val trace: Consumer<String> = { Log.log(LogLevel.Trace, it)}
        @JvmField val debug: Consumer<String> = { Log.log(LogLevel.Debug, it)}
        @JvmField val info: Consumer<String> = { Log.log(LogLevel.Info, it)}
        @JvmField val warn: Consumer<String> = { Log.log(LogLevel.Warn, it)}
        @JvmField val error: Consumer<String> = { Log.log(LogLevel.Error, it)}

        override fun toString(): String {
            return "Injectable.Logger"
        }
    }
}