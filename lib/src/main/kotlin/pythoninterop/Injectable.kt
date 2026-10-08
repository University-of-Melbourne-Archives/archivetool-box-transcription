package au.edu.unimelb.archivetool.pythoninterop

import org.graalvm.polyglot.Context

interface Injectable {
    abstract fun inject(ctx: Context)
}
