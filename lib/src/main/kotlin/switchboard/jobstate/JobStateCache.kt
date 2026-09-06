package au.edu.unimelb.archivetool.switchboard.jobstate

import java.nio.file.Path

// Do not use this class' constructor. It is only public because `getCache` needs to be inline.
class JobStateCache<S : JobState> {
    private val cache = mutableMapOf<Path, S>()

    fun get(idx: Path): S? = cache[idx]

    fun getOrTry(idx: Path, or: () -> S?): S? = cache[idx] ?: or()?.also { cache[idx] = it }

    fun put(idx: Path, jobState: S): S = jobState.also { cache[idx] = it }

    fun remove(idx: Path) = cache.remove(idx)

    companion object {
        val caches = mutableMapOf<JobStateVariant<*>, JobStateCache<*>>()

        inline fun <reified T : JobStateVariant<S>, S : JobState> getCache(
            variant: T
        ): JobStateCache<S> {
            return ((caches.entries.filter { it.key is T }.map { it.value }.firstOrNull()
                ?: run {
                    val v = JobStateCache<S>()
                    caches[variant] = v
                    v
                })
                as JobStateCache<S>)
        }
    }
}
