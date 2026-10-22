import au.edu.unimelb.archivetool.python.interop.PythonModule
import au.edu.unimelb.archivetool.python.interop.PythonScript
import au.edu.unimelb.archivetool.python.interop.injectables.Logger
import au.edu.unimelb.archivetool.python.interop.types.PythonBoolean
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.logging.consumers.StringConsumer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.junit.jupiter.api.Test

class PythonInteropTest {
    val testConsumer = StdoutConsumer(LogLevel.Trace)

    @BeforeTest
    fun init() {
        Log.registerConsumer(testConsumer)
    }

    @AfterTest
    fun destroy() {
        Log.revokeConsumer(testConsumer)
    }

    @Test
    fun `load a python script`() {
        PythonScript("test.py").eval()
    }

    @Test
    fun `inject and use logger in script`() {
        val logs = StringConsumer()
        Log.registerConsumer(logs)
        PythonScript("log.py", setOf(Logger())).eval()
        assert(logs.getLog().contains("Log is being called from python"))
        Log.revokeConsumer(logs)
    }

    @Test
    fun `load python module from fileset`() {
        PythonModule(setOf("test-module/module-1.py", "test-module/module-consumer.py"))
            .methods["test_consumer"]!!
            .execute()
    }

    @Test
    fun `load python module from folder`() {
        PythonModule("test-module").methods["test_consumer"]!!.execute()
    }

    @Test
    fun `interpret types from python module`() {
        val module = PythonModule("test-module")
        val pgBoolean = module.methods["returnBoolean"]!!.execute()
        val wrapperBoolean = PythonBoolean(module.context, pgBoolean)
        var test by wrapperBoolean::jvmValue

        assert(test && wrapperBoolean.jvmValue)

        test = false

        module.methods["assertFalse"]!!.execute(wrapperBoolean.polyglotValue)
    }
}
