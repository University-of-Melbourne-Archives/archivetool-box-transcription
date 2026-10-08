package au.edu.unimelb.archivetool.pythoninterop

import org.graalvm.polyglot.Context

interface Injectable {
    fun inject(ctx: Context)
}
