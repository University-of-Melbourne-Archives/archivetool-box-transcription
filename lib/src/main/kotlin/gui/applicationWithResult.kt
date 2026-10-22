package au.edu.unimelb.archivetool.gui

import au.edu.unimelb.archivetool.util.MutableWaitFlow
import au.edu.unimelb.archivetool.util.WaitFlow
import au.edu.unimelb.archivetool.util.logging.Log
import io.nacular.doodle.application.Application
import io.nacular.doodle.application.application
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.bindings.NoArgBindingDI

suspend inline fun <reified T : Application, R : Any> applicationWithResult(
    defaultResult: R,
    modules: List<DI.Module> = listOf(),
    coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    crossinline creator: NoArgBindingDI<*>.(MutableWaitFlow<R>) -> T,
): R {
    val resultFlow: WaitFlow<R> = MutableWaitFlow(defaultResult)
    lateinit var result: R
    Log.debug("Running GUI Application with Flow")
    application(modules) { this.creator(resultFlow as MutableWaitFlow) }
    val job =
        CoroutineScope(coroutineDispatcher).launch {
            result = resultFlow.collect {
                Log.trace("Recieved intermediate value from GUI Result Flow $it")
            }
            Log.debug("GUI Flow Closed.")
        }
    job.join()
    return result
}
