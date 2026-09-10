package au.edu.unimelb.archivetool.pythoninterop.injectables

import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.pythoninterop.Injectable
import java.util.function.Consumer
import org.graalvm.polyglot.Context

/*
The following is a stub for using this in Python Code:

```py
try:
    Log
except NameError:
    class Logger:
      def trace(self, msg):
        print("trace:", msg)
      def debug(self, msg):
        print("debug:", msg)
      def info(self, msg):
        print("info:", msg)
      def warn(self, msg):
        print("warn:", msg)
      def error(self, msg):
        print("error:", msg)
    Log = Logger()
```
*/

class Logger : Injectable {
    override fun inject(ctx: Context) {
        ctx.getBindings("python").putMember("Log", this)
    }

    @JvmField val trace: Consumer<String> = { Log.log(LogLevel.Trace, it) }
    @JvmField val debug: Consumer<String> = { Log.log(LogLevel.Debug, it) }
    @JvmField val info: Consumer<String> = { Log.log(LogLevel.Info, it) }
    @JvmField val warn: Consumer<String> = { Log.log(LogLevel.Warn, it) }
    @JvmField val error: Consumer<String> = { Log.log(LogLevel.Error, it) }

    override fun toString(): String {
        return "Injectable.Logger"
    }
}
