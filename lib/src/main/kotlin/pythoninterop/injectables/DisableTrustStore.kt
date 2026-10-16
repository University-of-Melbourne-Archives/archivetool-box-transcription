package au.edu.unimelb.archivetool.pythoninterop.injectables

import au.edu.unimelb.archivetool.pythoninterop.Injectable
import org.graalvm.polyglot.Context

// httpx (what openai uses to make its requests) reaches into CPython's OpenSSL to get system ssl
// certs.
// We aren't using cpython so we need to basically tell it to ignore the truststore
object DisableTrustStore : Injectable {
    val inject: String by lazy {
        """
        import sys, types
        stub = types.ModuleType("truststore")
        stub.inject_into_ssl = lambda: None
        stub.SSLContext = __import__("ssl").SSLContext
        sys.modules["truststore"] = stub
        """
            .trimIndent()
    }

    override fun inject(ctx: Context) {
        ctx.eval("python", inject)
    }

    override fun toString(): String {
        return "Injectable.DisableTrustStore"
    }
}
