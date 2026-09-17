import au.edu.unimelb.archivetool.logging.Log
import au.edu.unimelb.archivetool.logging.Log.expect
import au.edu.unimelb.archivetool.logging.LogLevel
import au.edu.unimelb.archivetool.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.logging.consumers.StringConsumer
import au.edu.unimelb.archivetool.switchboard.MockSwitchboard
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import au.edu.unimelb.archivetool.switchboard.jobstate.memory.ConcreteJobState
import au.edu.unimelb.archivetool.util.geometry.Quad
import kotlin.collections.mutableMapOf
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest

class SwitchboardTest {
    val testConsumer = StdoutConsumer(LogLevel.Trace)

    @BeforeTest
    fun init() {
        Log.registerConsumer(testConsumer)
    }

    @AfterTest
    fun destroy() {
        Log.revokeConsumer(testConsumer)
    }

    val cache by lazy {
        JobStateCache.getCache<JobStateVariant.Memory, ConcreteJobState>(JobStateVariant.Memory)
    }

    val cacheLock = Mutex()

    val directory by lazy {
        Path(
            {}.javaClass
                .getResource("/SwitchboardTest")
                .expect("Test couldn't find test folder")
                .path
        )
    }

    val fileNameRegex = "PXL_\\d{8}_\\d{9}(\\.clahe)?\\.jpg"

    @Test
    fun `call init interaction`() {
        val consumer = StringConsumer()
        Log.registerConsumer(consumer)

        MockSwitchboard.init(setOf())

        Log.revokeConsumer(consumer)
        assert(consumer.getLog().contains("Running Mock Init Interaction"))
    }

    @Test
    fun `call create interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        cacheLock.lock("createPreview")
        cache.withWriteLock {
            it.remove(directory)
        }

        MockSwitchboard.createPreview(
            directory,
            permitExisting = false,
            // We don't need weights as we are only calling the mock variant.
            weights = Path("."),
        )

        cacheLock.unlock("createPreview")
        Log.revokeConsumer(consumer)
        val log = consumer.getLog()
        assert(log.contains("Using Mock Implementation of Create Preview. Nothing will be done."))

        assert(log.contains("Files: [\n\t"))
        assertNotNull(("(?:$fileNameRegex,\n\t){15}".toRegex(RegexOption.MULTILINE).find(log)))
        assertNotNull(("\\(and \\d+ others\\)]".toRegex()).find(log))
    }

    @Test
    fun `call review interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        cacheLock.lock("reviewPreview")
        val cacheGuard = cache.writeLock()
        val cache = cacheGuard.value

        // TODO setup so that we can have a specific number of files go over auto-approval.
        cache.put(
            directory,
            ConcreteJobState(
                directory,
                starterState =
                    baseFiles
                        .mapValues {
                            if (it.value.vertexes == null) {
                                JobFile(approved = false, vertexes = mutableMapOf())
                            } else {
                                it.value
                            }
                        }
                        .mapValues {
                            it.value.apply {
                                vertexes!!["box-1"] =
                                    ArchiveBox(
                                        Boundary(
                                            Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                            confidence = 1.0,
                                        ),
                                        listOf(
                                            Boundary(
                                                Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                confidence = 0.5,
                                            )
                                        ),
                                    )
                            }
                        }
                        .toMutableMap(),
            ),
        )

        cacheGuard.release()

        MockSwitchboard.reviewPreview(directory)

        cacheLock.unlock("reviewPreview")
        Log.revokeConsumer(consumer)
        val log = consumer.getLog()

        assertNotNull(
            ("\\d+ total files, \\d+ above auto-approval threshold \\d(?:\\+\\.\\+\\d*)?"
                .toRegex(RegexOption.MULTILINE)
                .find(log))
        )
    }

    @Test
    fun `call image manipulation interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        cacheLock.lock("imageManip")
        val cacheGuard = cache.writeLock()
        val cache = cacheGuard.value

        cache.put(
            directory,
            ConcreteJobState(
                directory,
                starterState =
                    baseFiles
                        .mapValues {
                            it.value.run {
                                JobFile(
                                    approved = true,
                                    vertexes =
                                        mutableMapOf(
                                            "box-1" to
                                                ArchiveBox(
                                                    Boundary(
                                                        Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                        confidence = 1.0,
                                                    ),
                                                    listOf(
                                                        Boundary(
                                                            Quad.create(
                                                                0 to 0,
                                                                1 to 0,
                                                                1 to 1,
                                                                0 to 1,
                                                            ),
                                                            confidence = 0.5,
                                                        )
                                                    ),
                                                )
                                        ),
                                )
                            }
                        }
                        .toMutableMap(),
            ),
        )

        cacheGuard.release()

        MockSwitchboard.manipulateImages(directory)

        cacheLock.unlock("imageManip")
        Log.revokeConsumer(consumer)
        val log = consumer.getLog()

        assert(log.contains("Using mock image manipulation tool, nothing will happen."))
    }

    @Test
    fun `call ocr interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        cacheLock.lock("ocr")
        val cacheGuard = cache.writeLock()
        val cache = cacheGuard.value

        cache.put(
            directory,
            ConcreteJobState(
                directory,
                starterState =
                    baseFiles
                        .mapValues {
                            it.value.run {
                                JobFile(
                                    approved = true,
                                    vertexes =
                                        mutableMapOf(
                                            "box-1" to
                                                ArchiveBox(
                                                    Boundary(
                                                        Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                        confidence = 1.0,
                                                    ),
                                                    listOf(
                                                        Boundary(
                                                            Quad.create(
                                                                0 to 0,
                                                                1 to 0,
                                                                1 to 1,
                                                                0 to 1,
                                                            ),
                                                            confidence = 0.5,
                                                        )
                                                    ),
                                                )
                                        ),
                                )
                            }
                        }
                        .toMutableMap(),
            ),
        )

        cacheGuard.release()

        MockSwitchboard.createOCR(directory)

        cacheLock.unlock("ocr")
        Log.revokeConsumer(consumer)
        val log = consumer.getLog()

        assert(log.contains("Running Mock OCR, nothing will be done."))
        assertNotNull(
            ("(?:.*Transcribing $fileNameRegex::box-1-box\n.*Transcribing $fileNameRegex::box-1-label-1\n){55}"
                .toRegex(RegexOption.MULTILINE)
                .find(log))
        )
    }

    @Test
    fun `call export interaction`() = runTest {
        val consumer = StringConsumer(LogLevel.Trace)
        Log.registerConsumer(consumer)

        cacheLock.lock("export")
        val cacheGuard = cache.writeLock()
        val cache = cacheGuard.value

        cache.put(
            directory,
            ConcreteJobState(
                directory,
                starterState =
                    baseFiles
                        .mapValues {
                            it.value.run {
                                JobFile(
                                    approved = true,
                                    vertexes =
                                        mutableMapOf(
                                            "box-1" to
                                                ArchiveBox(
                                                    Boundary(
                                                        Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                        confidence = 1.0,
                                                        transcription = "test1",
                                                    ),
                                                    listOf(
                                                        Boundary(
                                                            Quad.create(
                                                                0 to 0,
                                                                1 to 0,
                                                                1 to 1,
                                                                0 to 1,
                                                            ),
                                                            confidence = 0.5,
                                                            transcription = "test2",
                                                        )
                                                    ),
                                                )
                                        ),
                                )
                            }
                        }
                        .toMutableMap(),
            ),
        )

        cacheGuard.release()

        MockSwitchboard.generateExport(directory)

        cacheLock.unlock("export")
        Log.revokeConsumer(consumer)
        val log = consumer.getLog()

        assertNotNull(
            ("(?:${fileNameRegex}::box-1-(box|label-1): test[12];;){110}"
                .toRegex(RegexOption.MULTILINE)
                .find(log))
        )
    }
}
