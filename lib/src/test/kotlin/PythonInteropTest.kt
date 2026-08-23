import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.logging.consumers.StringConsumer
import au.edu.unimelb.archivetool.pythoninterop.Injectable
import au.edu.unimelb.archivetool.pythoninterop.PythonModule
import au.edu.unimelb.archivetool.pythoninterop.PythonScript
import au.edu.unimelb.archivetool.pythoninterop.types.PythonBoolean
import java.lang.ref.WeakReference
import kotlin.test.BeforeTest
import org.junit.jupiter.api.Test

class PythonInteropTest {
    @BeforeTest
    fun init() {
        Log.registerConsumer(StdoutConsumer(LogLevel.Trace))
    }

    @Test
    fun `load a python script`() {
        PythonScript("test.py").eval()
    }

    @Test
    fun `inject and use logger in script`() {
        val logs = StringConsumer()
        Log.registerConsumer(logs)
        PythonScript("log.py", setOf(Injectable.Logger())).eval()
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
        val wrapperBoolean = PythonBoolean(WeakReference(module.context), pgBoolean)
        var test by wrapperBoolean::jvmValue

        assert(test && wrapperBoolean.jvmValue)

        test = false

        module.methods["assertFalse"]!!.execute(wrapperBoolean.polyglotValue)
    }
}
