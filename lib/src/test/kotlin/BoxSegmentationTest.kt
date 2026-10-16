import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.getBoxes
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.time.Clock

class BoxSegmentationTest {
    @SemiManual(
        "Manually check the output: It is not deterministic.",
        lastSucceeded = "6803f319b1091d69b1b021c34630f14adde516da",
    )
    @Test
    fun `test_model_detections`() {
        val image =
            JobRelativePath(
                Path(".").toAbsolutePath().parent,
                Path("src/test/resources/SwitchboardTest/PXL_20251127_230150462.jpg"),
            )
        // Relative paths resolve against the working directory, so print it if a file can't be
        // found
        println("Working dir: ${Path(".").toAbsolutePath().parent}")
        require(image.absolute.exists()) { "Image not found: ${image.absolute}" }

        val start = Clock.System.now()
        val result = getBoxes(image)
        println("Inference took ${Clock.System.now() - start}")

        result.let { (name, det) ->
            println(name)
            for ((id, entry) in det.vertexes!!) {
                println(
                    "  $id  conf=%.3f  %s"
                        .format(entry.boxBoundary.confidence, entry.boxBoundary.points)
                )
                for (s in entry.labelBoundaries) {
                    println("    sticker  conf=%.3f  %s".format(s.confidence, s.points))
                }
            }
        }
    }
}
