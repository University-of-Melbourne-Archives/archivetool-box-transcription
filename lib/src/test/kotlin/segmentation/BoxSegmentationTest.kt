package segmentation

import SemiManual
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.DEFAULT_MODEL_RESOURCE
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.Detection
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.cornerPoints
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.getBoxes
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.iou
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.letterbox
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.suppressOverlaps
import au.edu.unimelb.archivetool.segmentation.BoxSegmentation.toArchiveBoxes
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.error.expect
import au.edu.unimelb.archivetool.util.math.geometry.Point
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import java.io.FileNotFoundException
import kotlin.collections.iterator
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

class BoxSegmentationTest {
    val directory by lazy {
        {}.javaClass
            .getResource("/SwitchboardTest")
            .expect("Test couldn't find test folder")
            .toURI()
            .toPath()
    }

    val image by lazy { JobRelativePath(directory, Path("PXL_20251127_230150462.jpg")) }

    fun rect(x1: Int, y1: Int, x2: Int, y2: Int) =
        Polygon.create(x1 to y1, x2 to y1, x2 to y2, x1 to y2)

    @SemiManual(
        "Manually check the output: It is not deterministic.",
        lastSucceeded = "6803f319b1091d69b1b021c34630f14adde516da",
    )
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true")
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
        val result = BoxSegmentation.getBoxes(image)
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

    @Test
    fun iouOfIdenticalBoxesIsOne() {
        assertEquals(1.0, iou(rect(0, 0, 10, 10), rect(0, 0, 10, 10)))
    }

    @Test
    fun iouOfDisjointBoxesIsZero() {
        assertEquals(0.0, iou(rect(0, 0, 10, 10), rect(20, 20, 30, 30)))
        // Touching edges share no area
        assertEquals(0.0, iou(rect(0, 0, 10, 10), rect(10, 0, 20, 10)))
    }

    @Test
    fun iouOfHalfOverlap() {
        // 50 shared over a union of 150
        assertEquals(1.0 / 3, iou(rect(0, 0, 10, 10), rect(5, 0, 15, 10)), 1e-9)
    }

    @Test
    fun iouUsesBoundingBoxes() {
        // A triangle is compared by its bounding box, so matches the square around it
        val triangle = Polygon.create(0 to 0, 10 to 0, 0 to 10)
        assertEquals(1.0, iou(triangle, rect(0, 0, 10, 10)))
    }

    @Test
    fun suppressOverlapsKeepsMostConfidentDuplicate() {
        val kept =
            listOf(
                    Detection("Box", 0.6, rect(0, 0, 10, 10)),
                    Detection("Box", 0.9, rect(1, 1, 11, 11)),
                )
                .suppressOverlaps()
        assertEquals(listOf(0.9), kept.map { it.score })
    }

    @Test
    fun suppressOverlapsKeepsSeparateBoxes() {
        val kept =
            listOf(
                    Detection("Box", 0.6, rect(0, 0, 10, 10)),
                    Detection("Box", 0.9, rect(5, 0, 15, 10)),
                    Detection("Box", 0.7, rect(50, 50, 60, 60)),
                )
                .suppressOverlaps()
        // Sorted by confidence, and the 1/3 overlap is under the threshold
        assertEquals(listOf(0.9, 0.7, 0.6), kept.map { it.score })
    }

    @Test
    fun suppressOverlapsIgnoresOtherClasses() {
        // A sticker sits inside its box, so must not be suppressed by it
        val kept =
            listOf(
                    Detection("Box", 0.9, rect(0, 0, 10, 10)),
                    Detection("Sticker", 0.8, rect(0, 0, 10, 10)),
                )
                .suppressOverlaps()
        assertEquals(listOf("Box", "Sticker"), kept.map { it.label })
    }

    @Test
    fun suppressOverlapsRespectsThreshold() {
        val dets =
            listOf(
                Detection("Box", 0.9, rect(0, 0, 10, 10)),
                Detection("Box", 0.8, rect(5, 0, 15, 10)),
            )
        assertEquals(1, dets.suppressOverlaps(iouThreshold = 0.2).size)
        assertEquals(2, dets.suppressOverlaps(iouThreshold = 0.5).size)
        assertEquals(listOf<Detection>(), listOf<Detection>().suppressOverlaps())
    }

    @Test
    fun toArchiveBoxesAssignsStickerOutsideBoxToOverlappingBox() {
        val boxes =
            listOf(
                    Detection("Box", 0.9, rect(0, 0, 100, 100)),
                    Detection("Box", 0.8, rect(200, 0, 300, 100)),
                    Detection("Sticker", 0.7, rect(40, 40, 60, 60)),
                    // Centred just past the right edge of the second box
                    Detection("Sticker", 0.6, rect(290, 40, 320, 60)),
                )
                .toArchiveBoxes()
        assertEquals(listOf(0.7), boxes.getValue("box-1").labelBoundaries.map { it.confidence })
        assertEquals(listOf(0.6), boxes.getValue("box-2").labelBoundaries.map { it.confidence })
    }

    @Test
    fun toArchiveBoxesOnlyGivesUnassignedStickersToStickerlessBoxes() {
        val boxes =
            listOf(
                    Detection("Box", 0.9, rect(0, 0, 100, 100)),
                    Detection("Box", 0.8, rect(200, 0, 300, 100)),
                    Detection("Sticker", 0.7, rect(40, 40, 60, 60)),
                    // Overlaps the first box, which already has a sticker
                    Detection("Sticker", 0.6, rect(95, 40, 120, 60)),
                    // Both overlap the second box, so the more confident wins
                    Detection("Sticker", 0.5, rect(290, 40, 320, 60)),
                    Detection("Sticker", 0.4, rect(290, 70, 320, 90)),
                    // Overlaps no box, so belongs to one out of frame
                    Detection("Sticker", 0.3, rect(500, 500, 510, 510)),
                )
                .toArchiveBoxes()
        assertEquals(listOf(0.7), boxes.getValue("box-1").labelBoundaries.map { it.confidence })
        assertEquals(listOf(0.5), boxes.getValue("box-2").labelBoundaries.map { it.confidence })
        // A box with no sticker nearby is left without one
        assertEquals(
            listOf<Double>(),
            listOf(Detection("Box", 0.9, rect(0, 0, 100, 100)))
                .toArchiveBoxes()
                .getValue("box-1")
                .labelBoundaries
                .map { it.confidence },
        )
    }

    @Test
    fun cornerPointsOfRectangleAreItsCorners() {
        // Extra points along the edges are dropped by the hull
        val poly =
            Polygon.create(0 to 0, 50 to 0, 100 to 0, 100 to 40, 100 to 80, 50 to 80, 0 to 80)
        val quad = poly.cornerPoints()
        assertEquals(
            setOf(
                Point.create(0, 0),
                Point.create(100, 0),
                Point.create(100, 80),
                Point.create(0, 80),
            ),
            quad.vertices.toSet(),
        )
    }

    @Test
    fun cornerPointsOfConcaveShapeIsQuad() {
        // An L shape's hull has 5 points, which must be reduced to a quadrilateral
        val poly = Polygon.create(0 to 0, 100 to 0, 100 to 100, 50 to 100, 50 to 50, 0 to 50)
        val quad = poly.cornerPoints()
        assertEquals(4, quad.vertices.size)
        // Every corner stays within the original bounds
        assertTrue(quad.vertices.all { it.x in 0..100 && it.y in 0..100 })
    }

    @Test
    fun letterboxWideImagePadsTopAndBottom() {
        // 200x100 into 640x640 scales by 3.2, to 640x320, leaving 160 above and below
        val lb = letterbox(Mat(100, 200, CV_8UC3, Scalar(0.0)), 640, 640)
        assertEquals(3.2, lb.r, 1e-9)
        assertEquals(640 to 320, lb.newW to lb.newH)
        assertEquals(0 to 160, lb.left to lb.top)
        assertEquals(640 to 640, lb.padded.cols() to lb.padded.rows())
        // Padding is grey and the image itself is untouched
        assertEquals(114, lb.padded.ptr(0, 0).get(0).toInt() and 0xFF)
        assertEquals(0, lb.padded.ptr(320, 320).get(0).toInt() and 0xFF)
        lb.padded.release()
    }

    @Test
    fun letterboxTallImagePadsSides() {
        val lb = letterbox(Mat(200, 100, CV_8UC3, Scalar(0.0)), 640, 640)
        assertEquals(320 to 640, lb.newW to lb.newH)
        assertEquals(160 to 0, lb.left to lb.top)
        assertEquals(640 to 640, lb.padded.cols() to lb.padded.rows())
        lb.padded.release()
    }

    @Test
    fun letterboxOddPaddingStillFillsInput() {
        // 323 rows leaves 317 to pad, split 158 above and 159 below
        val lb = letterbox(Mat(101, 200, CV_8UC3, Scalar(0.0)), 640, 640)
        assertEquals(323, lb.newH)
        assertEquals(158, lb.top)
        assertEquals(640 to 640, lb.padded.cols() to lb.padded.rows())
        lb.padded.release()
    }

    @Test
    fun copyModelIsCachedAndExists() {
        val first = BoxSegmentation.copyModelFromResourcesIfNotExists(DEFAULT_MODEL_RESOURCE)
        val second = BoxSegmentation.copyModelFromResourcesIfNotExists(DEFAULT_MODEL_RESOURCE)
        assertTrue(first.exists())
        assertEquals(first, second)
    }

    @Test
    fun getBoxesMissingImageThrows() {
        assertFailsWith<FileNotFoundException> {
            getBoxes(JobRelativePath(directory, Path("does-not-exist.jpg")))
        }
    }

    @Test
    fun getBoxesKeysImageByPath() {
        assertEquals(image, getBoxes(image).first)
    }

    @Test
    fun getBoxesOrdersBoxesByConfidence() {
        val vertexes = getBoxes(image).second.vertexes!!
        assertTrue(vertexes.isNotEmpty(), "Expected the test image to have boxes")
        assertEquals((1..vertexes.size).map { "box-$it" }.toSet(), vertexes.keys)

        val confidences =
            (1..vertexes.size).map { vertexes.getValue("box-$it").boxBoundary.confidence }
        assertEquals(confidences.sortedDescending(), confidences)
    }

    @Test
    fun getBoxesRespectsConfidenceThreshold() {
        val conf = 0.7
        val boundaries =
            getBoxes(image, conf = conf).second.vertexes!!.values.flatMap {
                it.keyedBoundaries.values
            }
        assertTrue(boundaries.all { it.confidence >= conf })
        // Scores are sigmoids, so nothing can reach above 1
        assertTrue(getBoxes(image, conf = 1.01).second.vertexes!!.isEmpty())
    }

    @Test
    fun getBoxesStickersSitInsideTheirBox() {
        for (box in getBoxes(image).second.vertexes!!.values) {
            assertEquals(4, box.boxBoundary.points.vertices.size)
            for (sticker in box.labelBoundaries) {
                assertEquals(4, sticker.points.vertices.size)
                assertTrue(box.boxBoundary.points.containsPoint(sticker.points.centroid))
            }
        }
    }

    @Test
    fun getBoxesEdgeMarginDropsBoxes() {
        val all = getBoxes(image, edgeMargin = 0.0).second.vertexes!!.size
        val default = getBoxes(image).second.vertexes!!.size
        assertTrue(default <= all)
        // No box can be centred more than halfway from every side
        assertTrue(getBoxes(image, edgeMargin = 0.5).second.vertexes!!.isEmpty())
    }
}
