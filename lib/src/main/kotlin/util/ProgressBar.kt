package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.logging.ConsumerSettings
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogConsumer
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StringConsumer
import au.edu.unimelb.archivetool.util.math.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// TODO: We should really have a TUI-sytle implementation of this, and add a gui one if we ever write a full gui
class CLIProgressBar(totalItems: Int?) : ProgressBar(totalItems) {
    var logConsumer: LogConsumer? = null
    var lastLog = ""
    var tick = 0
    override fun run(operation: ProgressBarScope.() -> Unit) {
        logConsumer = object : LogConsumer {
            val inner = StringConsumer(settings = ConsumerSettings(enableAnsiColor = true))
            override val maxLogLevel: LogLevel
                get() = inner.maxLogLevel
            override val settings: ConsumerSettings
                get() = inner.settings

            override fun recordLogEntry(logEntry: String) {
                inner.recordLogEntry(logEntry)
                redraw()
            }

            override fun toString(): String = inner.toString()
        }
        Log.registerConsumer(logConsumer!!)
        val handle = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(2.seconds / spinnerPattern.length)
                tick++
                tick %= spinnerPattern.length
                redraw()
            }
        }
        super.run(operation)
        handle.cancel()
        Log.revokeConsumer(logConsumer!!)
        println("")
        lastLog = ""
        logConsumer = null
    }

    override fun redraw() {
        print(
            buildString {
                logConsumer!!.toString().let { l ->
                    if (lastLog == l) {
                        append("\r")
                    } else {
                        append('\u000c')
                        appendLine(logConsumer!!.toString())
                        lastLog = l
                    }
                }
                append(drawProgress())
            }
        )
        System.out.flush()
    }

    val totalString by lazy {
        totalItems?.let {"/$it"} ?: ""
    }
    val spinnerPattern = "|/\u2014\\"
    private fun drawProgress(): String = "${spinnerPattern[tick]} [${
        progress.toString().padStart(totalItems?.toString()?.length ?: 0, '0')
    }$totalString]${
        if (totalItems != null) {
            val screenRemaining = 80 - "| [$totalItems$totalString] () ".length
            val (hashes, spaces) = (progress.toDouble() / totalItems.toDouble()).lerp(0.0, screenRemaining.toDouble()).roundToInt().let {
                "#".repeat(it) to " ".repeat(screenRemaining - it)
            }
            " ($hashes$spaces) "
        } else ""
    }"
}

abstract class ProgressBar(val totalItems: Int?, starterProgress: Int = 1) {
    protected var progress: Int = starterProgress
    open fun run(operation: ProgressBarScope.() -> Unit) {
        redraw()
        val scope = ProgressBarScope()
        scope.operation()
    }

    protected abstract fun redraw()

    inner class ProgressBarScope {
        fun increment(n: Int = 1): Unit {
            progress += n
            totalItems?.takeIf { progress > totalItems } ?.let {
                progress = totalItems
                return finish()
            }
            redraw()
        }
        fun setProgress(n: Int) {
            progress = n
            redraw()
        }
        fun cancel() {
        }
        fun finish() {

        }
    }

    companion object {
        // TODO: Should this default implementation be defined in `app`?

        fun withProgressBar(
            total: Int? = null,
            implementation: ProgressBar = CLIProgressBar(total),
            operation: ProgressBarScope.() -> Unit
        ) = implementation.run(operation)
    }
}