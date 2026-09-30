import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateCache
import au.edu.unimelb.archivetool.switchboard.jobstate.JobStateVariant
import au.edu.unimelb.archivetool.switchboard.jobstate.RecordSetID
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobState
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobStateInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONJobStateWriterThread
import au.edu.unimelb.archivetool.switchboard.jobstate.json.JSONString.Companion.toStrictString
import au.edu.unimelb.archivetool.util.DateRange
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.assertDoesNotThrow

class JSONJobStateTest {
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
        JobStateCache.getCache<JobStateVariant.JSON, JSONJobState>(JobStateVariant.JSON)
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

    val jsonPath: Path by lazy { directory.resolve(GENERATED_PATH).resolve("job.json") }

    @Test
    fun `export json`() = runTest {
        cacheLock.lock("exportJson")
        jsonPath.deleteIfExists()

        JSONJobStateInteraction()
            .create(directory)
            .update(
                baseFiles
                    .mapKeys { JobRelativePath(directory, it.key) }
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
                                                    rawMetadata =
                                                        BoxMetadata(
                                                            null,
                                                            null,
                                                            null,
                                                            description = "test1",
                                                        ),
                                                ),
                                                listOf(
                                                    Boundary(
                                                        Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                        confidence = 0.5,
                                                        rawMetadata =
                                                            BoxMetadata(
                                                                RecordSetID.OldID("1976.0276"),
                                                                1,
                                                                DateRange(
                                                                    LocalDate(
                                                                        year = 1971,
                                                                        month = 1,
                                                                        day = 1,
                                                                    ),
                                                                    null,
                                                                ),
                                                                description = "test1",
                                                            ),
                                                    )
                                                ),
                                                metadata =
                                                    BoxMetadata(
                                                        RecordSetID.OldID("1976.0276"),
                                                        1,
                                                        DateRange(
                                                            LocalDate(
                                                                year = 1971,
                                                                month = 1,
                                                                day = 1,
                                                            ),
                                                            null,
                                                        ),
                                                        description = "test1;test2",
                                                    ),
                                            )
                                    ),
                            )
                        }
                    }
                    .toMutableMap()
            )

        JSONJobStateWriterThread.waitWhileWriting()

        assertDoesNotThrow {
            Json.parseToJsonElement(
                jsonPath.readText().also { Log.debug("${jsonPath.pathString} contains \"$it\"") }
            )
        }

        cacheLock.unlock("exportJson")
    }

    @Test
    fun `export json multiple times and ensure correct output`() = runTest {
        cacheLock.lock("exportMultiJson")
        jsonPath.deleteIfExists()

        val state = JSONJobStateInteraction().create(directory)

        state.update(
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
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
                                                rawMetadata =
                                                    BoxMetadata(
                                                        null,
                                                        null,
                                                        null,
                                                        description = "test1",
                                                    ),
                                            ),
                                            listOf(
                                                Boundary(
                                                    Quad.create(0 to 0, 1 to 0, 1 to 1, 0 to 1),
                                                    confidence = 0.5,
                                                    rawMetadata =
                                                        BoxMetadata(
                                                            RecordSetID.OldID("1976.0276"),
                                                            1,
                                                            DateRange(
                                                                LocalDate(
                                                                    year = 1971,
                                                                    month = 1,
                                                                    day = 1,
                                                                ),
                                                                null,
                                                            ),
                                                            description = "test1",
                                                        ),
                                                )
                                            ),
                                            metadata =
                                                BoxMetadata(
                                                    RecordSetID.OldID("1976.0276"),
                                                    1,
                                                    DateRange(
                                                        LocalDate(year = 1971, month = 1, day = 1),
                                                        null,
                                                    ),
                                                    description = "test1;test2",
                                                ),
                                        )
                                ),
                        )
                    }
                }
                .toMutableMap()
        )
        state.update(
            baseFiles
                .mapKeys { JobRelativePath(directory, it.key) }
                .mapValues {
                    it.value.run {
                        JobFile(
                            approved = true,
                            vertexes =
                                mutableMapOf(
                                    "box-1" to
                                        ArchiveBox(
                                            Boundary(
                                                Quad.create(0 to 0, 100 to 0, 100 to 100, 0 to 100),
                                                confidence = 1.0,
                                                rawMetadata =
                                                    BoxMetadata(
                                                        null,
                                                        null,
                                                        null,
                                                        description = "test1",
                                                    ),
                                            ),
                                            listOf(
                                                Boundary(
                                                    Quad.create(
                                                        0 to 0,
                                                        100 to 0,
                                                        100 to 100,
                                                        0 to 100,
                                                    ),
                                                    confidence = 0.5,
                                                    rawMetadata =
                                                        BoxMetadata(
                                                            RecordSetID.OldID("1976.0276"),
                                                            1,
                                                            DateRange(
                                                                LocalDate(
                                                                    year = 1971,
                                                                    month = 1,
                                                                    day = 1,
                                                                ),
                                                                null,
                                                            ),
                                                            description = "test1",
                                                        ),
                                                )
                                            ),
                                        )
                                ),
                        )
                    }
                }
                .filter { it.key.relative.pathString.contains("2311") }
                .toMutableMap()
        )

        JSONJobStateWriterThread.waitWhileWriting()

        val json = assertDoesNotThrow { Json.parseToJsonElement(jsonPath.readText()) }

        assertNotNull(
            json.jsonObject[baseFiles.keys.first { it.pathString.contains("2311") }.pathString]
                ?.jsonObject["vertexes"]
                ?.toStrictString()
                ?.contains("{\"x\":100,\"y\":100}")
                .takeIf { true }
        )

        assertNotNull(
            json.jsonObject[baseFiles.keys.first { !it.pathString.contains("2311") }.pathString]
                ?.jsonObject["vertexes"]
                ?.toStrictString()
                ?.contains("{\"x\":1,\"y\":1}")
                .takeIf { true }
        )

        cacheLock.unlock("exportMultiJson")
    }
}
