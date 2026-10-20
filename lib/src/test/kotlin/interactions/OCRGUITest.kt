package interactions

import SemiManual
import au.edu.unimelb.archivetool.switchboard.interactions.OCRGUIInteraction
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.BoxMetadata
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.switchboard.jobstate.RecordSetID
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.logging.Log
import au.edu.unimelb.archivetool.util.logging.LogLevel
import au.edu.unimelb.archivetool.util.logging.consumers.StdoutConsumer
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import kotlin.io.path.toPath

class OCRGUITest {
    val testConsumer = StdoutConsumer(LogLevel.Trace)

    @BeforeTest
    fun init() {
        Log.registerConsumer(testConsumer)
    }

    @AfterTest
    fun destroy() {
        Log.revokeConsumer(testConsumer)
    }

    val directory by lazy {
        {}.javaClass
            .getResource("/SwitchboardTest")
            .expect("Test couldn't find test folder")
            .toURI()
            .toPath()
    }

    @SemiManual(
        "Manually Interact with the GUI.",
        lastSucceeded = "bfb9ca7fe545ce26860539588340ef886e373927",
    )
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
    @Test
    fun `try review interaction`() {
        val files =
            mapOf(
                    Path("PXL_20251127_230150462.jpg") to
                        JobFile(
                            vertexes =
                                mutableMapOf(
                                    "box-1" to
                                        ArchiveBox(
                                            boxBoundary =
                                                Boundary(
                                                    points =
                                                        Quad.create(
                                                            100 to 100,
                                                            900 to 100,
                                                            900 to 1900,
                                                            100 to 1200,
                                                        ),
                                                    confidence = 0.9,
                                                ),
                                            labelBoundaries =
                                                listOf(
                                                    Boundary(
                                                        points =
                                                            Quad.create(
                                                                200 to 200,
                                                                800 to 200,
                                                                800 to 700,
                                                                200 to 700,
                                                            ),
                                                        confidence = 0.9,
                                                    )
                                                ),
                                            metadata =
                                                BoxMetadata(
                                                    RecordSetID.OldID("1976.0276"),
                                                    1071,
                                                    null,
                                                    description =
                                                        "Accounts; Balance Sheets & other   \n" +
                                                            "Accounting Material; 1929 F.W. Moore;   \n" +
                                                            "Geelong & W.D. Pres. Co.; H. Jones & Co   \n" +
                                                            "(Sth Africa); Western Province Pres. Co.;   \n" +
                                                            "Victoria Cross; Queensland State   \n" +
                                                            "Cannery; Kirkpatrick; Thompson & Hills;   \n" +
                                                            "Dunedin Jam Co.; Oakleaf; W.D. Peacock;   \n" +
                                                            "H. Jones & Co. (Adel); 1930 F.W. Moore;   \n" +
                                                            "Peacock Jam Co.; Henry Jones (Sth   \n" +
                                                            "Africa) & Durban Branch; Western   \n" +
                                                            "Province; Kirkpatrick; Thompson & Hills;   \n" +
                                                            "Oakleaf; H. Jones & (Adel); Victoria Cross;   \n" +
                                                            "J.E. Barnard; Geelong & W.D. Pres. Co.;   \n" +
                                                            "1931 Bendigo Pres. Co.; Peacock Jam; S.   \n" +
                                                            "Kirkpatrick; Nielson Fruit growing & Dev.;   \n" +
                                                            "Thompson & Hills; H.J. (Adelaide); F.W.   \n" +
                                                            "Moore; Western Province; Victoria Cross;   \n" +
                                                            "H. Jones (Sth Africa) & Durban Branch  ",
                                                ),
                                        ),
                                    "box-2" to
                                        ArchiveBox(
                                            boxBoundary =
                                                Boundary(
                                                    points =
                                                        Quad.create(
                                                            100 to 100,
                                                            900 to 100,
                                                            900 to 1900,
                                                            100 to 1900,
                                                        ),
                                                    confidence = 0.9,
                                                ),
                                            labelBoundaries =
                                                listOf(
                                                    Boundary(
                                                        points =
                                                            Quad.create(
                                                                200 to 200,
                                                                800 to 200,
                                                                800 to 700,
                                                                200 to 700,
                                                            ),
                                                        confidence = 0.9,
                                                    )
                                                ),
                                            metadata =
                                                BoxMetadata(
                                                    RecordSetID.OldID("1976.0276"),
                                                    1071,
                                                    null,
                                                    description =
                                                        "THIS IS THE SECOND BOX   \n" +
                                                            "Cannery; Kirkpatrick; Thompson & Hills;   \n" +
                                                            "Dunedin Jam Co.; Oakleaf; W.D. Peacock;   \n" +
                                                            "H. Jones & Co. (Adel); 1930 F.W. Moore;   \n" +
                                                            "Peacock Jam Co.; Henry Jones (Sth   \n" +
                                                            "Africa) & Durban Branch; Western   \n" +
                                                            "Province; Kirkpatrick; Thompson & Hills;   \n" +
                                                            "Oakleaf; H. Jones & (Adel); Victoria Cross;   \n" +
                                                            "J.E. Barnard; Geelong & W.D. Pres. Co.;   \n" +
                                                            "1931 Bendigo Pres. Co.; Peacock Jam; S.   \n" +
                                                            "Kirkpatrick; Nielson Fruit growing & Dev.;   \n" +
                                                            "Thompson & Hills; H.J. (Adelaide); F.W.   \n" +
                                                            "Moore; Western Province; Victoria Cross;   \n" +
                                                            "H. Jones (Sth Africa) & Durban Branch  ",
                                                ),
                                        ),
                                )
                        ),
                    Path("PXL_20251127_230207837.jpg") to
                        JobFile(
                            vertexes =
                                mutableMapOf(
                                    "box-1" to
                                        ArchiveBox(
                                            boxBoundary =
                                                Boundary(
                                                    points =
                                                        Quad.create(
                                                            100 to 100,
                                                            900 to 100,
                                                            900 to 1200,
                                                            100 to 1200,
                                                        ),
                                                    confidence = 0.9,
                                                ),
                                            labelBoundaries =
                                                listOf(
                                                    Boundary(
                                                        points =
                                                            Quad.create(
                                                                200 to 200,
                                                                800 to 200,
                                                                800 to 700,
                                                                200 to 700,
                                                            ),
                                                        confidence = 0.9,
                                                    )
                                                ),
                                            metadata =
                                                BoxMetadata(
                                                    null,
                                                    null,
                                                    null,
                                                    description =
                                                        "Accounts; Balance Sheets; Bendigo   \n" +
                                                            "Preserving Co. Ltd, 1925, 1926, 1929,   \n" +
                                                            "1930, 1934-36, 1939-56 & taxation   \n" +
                                                            "material; Geelong & Western District   \n" +
                                                            "Preserving Co. Ltd, 1914-1918, 1920-26,   \n" +
                                                            "1928-30; Tatura Cultivation Co. Pty Ltd,   \n" +
                                                            "1939-53, 1955; Ardmona Fruit Products   \n" +
                                                            "Co-op Co Ltd, 1931; H. Jones & Co (Adel)   \n" +
                                                            "Pty Ltd, Company tax return 1952; Henry   \n" +
                                                            "Jones Co-operative Ltd, Balance sheets,   \n" +
                                                            "1917, 1919, 1950-56; Notes on companies   \n" +
                                                            "of consolidated balance sheets &   \n" +
                                                            "accounts 1956  ",
                                                ),
                                        )
                                )
                        ),
                )
                .mapKeys { JobRelativePath(directory, it.key) }
                .toMutableMap()
        println(OCRGUIInteraction().review(files).mapValues { (_, v) -> Json.encodeToString(v) })
    }
}
