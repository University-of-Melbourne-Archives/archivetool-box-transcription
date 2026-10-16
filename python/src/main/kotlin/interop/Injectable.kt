package au.edu.unimelb.archivetool.python.interop

import org.graalvm.polyglot.Context

interface Injectable {
    fun inject(ctx: Context)
}
