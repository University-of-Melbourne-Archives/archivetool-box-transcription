package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.concurrency.RWLock
import java.nio.file.Path

// Do not use this class' constructor. It is only public because `getCache` needs to be inline.
class JobStateCache<S : JobState> {
    private val cache = mutableMapOf<Path, S>()

    fun get(idx: Path): S? = cache[idx]

    fun getOrTry(idx: Path, or: () -> S?): S? = cache[idx] ?: or()?.also { cache[idx] = it }

    fun put(idx: Path, jobState: S): S = jobState.also { cache[idx] = it }

    fun remove(idx: Path) = cache.remove(idx)

    companion object {
        val caches = mutableMapOf<JobStateVariant<*>, RWLock<JobStateCache<*>>>()

        inline fun <
            reified T : JobStateVariant<S>,
            reified C : RWLock<JobStateCache<S>>,
            S : JobState,
        > getCache(variant: T): C {
            return ((caches.entries.filter { it.key is T }.map { it.value as C }.firstOrNull()
                ?: run {
                    val v = RWLock<JobStateCache<*>>(JobStateCache<S>())
                    caches[variant] = v
                    v as C
                }))
        }

        @Deprecated(
            "Not strictly-typed reliably.",
            ReplaceWith(
                "getCache<T, RWLock<JobStateCache<S>>, S>()",
                "au.edu.unimelb.archivetool.util.concurrency.RWLock",
                "import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache.Companion.getCacheOld",
            ),
            DeprecationLevel.WARNING,
        )
        inline fun <reified T : JobStateVariant<S>, S : JobState> getCacheOld(
            variant: T
        ): RWLock<JobStateCache<S>> = getCache<T, RWLock<JobStateCache<S>>, S>(variant)
    }
}
