package au.edu.unimelb.archivetool.switchboard.jobstate

import java.nio.file.Path
import kotlin.reflect.KClass

// Do not use this class' constructor. It is only public because `getCache` needs to be inline.
class JobStateCache<T : JobState> {
    private val cache = mutableMapOf<Path, T>()

    fun get(idx: Path): T? = cache[idx]

    fun getOrTry(idx: Path, or: () -> T?): T? = cache[idx] ?: or()?.also { cache[idx] = it }

    fun put(idx: Path, jobState: T): T = jobState.also { cache[idx] = it }

    companion object {
        val caches = mutableMapOf<KClass<*>, JobStateCache<*>>()

        inline fun <reified T : JobState> getCache(): JobStateCache<T> {
            return ((caches.entries.filter { it.key is T }.map { it.value }.firstOrNull()
                ?: run {
                    val v = JobStateCache<T>()
                    caches[T::class] = v
                    v
                })
            // This warning is due to the unchecked cast,
            // however, we pretty much have to do this due to type erasure.
            as JobStateCache<T>)
        }
    }
}
