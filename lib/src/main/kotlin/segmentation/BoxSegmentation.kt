package au.edu.unimelb.archivetool.segmentation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.MonoColour
import au.edu.unimelb.archivetool.util.extension.asFileAttributes
import au.edu.unimelb.archivetool.util.file.copyFromResources
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import au.edu.unimelb.archivetool.util.wrap.CV
import au.edu.unimelb.archivetool.util.wrap.CV.subMat
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_core.CV_8U
import org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE
import org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR
import org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL
import org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY
import org.bytedeco.opencv.opencv_core.Mat

object BoxSegmentation {
    const val BOX_CLASS = "Box"
    const val STICKER_CLASS = "Sticker"
    const val DEFAULT_MODEL_RESOURCE = "model-sandbox/weights/yolo26n-archive-boxes-14-09.onnx"
    val modelDirectory by lazy {
        createTempDirectory(
            "archivetool-models",
            *PosixFilePermissions.fromString("rwxrwxr--").asFileAttributes(),
        )
    }

    val alreadyLoaded by lazy { mutableMapOf<String, Path>() }

    fun copyModelFromResourcesIfNotExists(resourcePath: String): Path {
        return alreadyLoaded.getOrPut(resourcePath) {
            copyFromResources(resourcePath, modelDirectory)
        }
    }

    private class Detection(val label: String, val score: Double, val polygon: Polygon)

    private class Model(
        val session: OrtSession,
        val names: Map<Int, String>,
        val inH: Int,
        val inW: Int,
    )

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val models: MutableMap<String, Model> by lazy { mutableMapOf() }

    /**
     * Segments an image into Box & Sticker polygons and returns detections as quadrilaterals, keyed
     * by image file name.
     *
     * Boxes whose visible part is centred within [edgeMargin] (a fraction of the image width or
     * height) of a side are dropped, as they are mostly out of frame. 0.0 disables this.
     */
    fun getBoxes(
        imagePath: JobRelativePath,
        modelPath: Path? = null,
        conf: Double = 0.5,
        edgeMargin: Double = 0.1,
    ): Pair<JobRelativePath, JobFile> {
        val modelPath = modelPath ?: copyModelFromResourcesIfNotExists(DEFAULT_MODEL_RESOURCE)

        val dets =
            runModel(imagePath, modelPath, conf, edgeMargin)
                .filter { it.polygon.vertices.size >= 3 }
                .suppressOverlaps()
        val vertexes = mutableMapOf<String, ArchiveBox>()

        // Key every box as box-N in confidence order
        val boxQuads = mutableListOf<Pair<String, Quad>>()
        dets
            .filter { it.label == BOX_CLASS }
            .sortedByDescending { it.score }
            .forEachIndexed { n, d ->
                val seg = Boundary(d.polygon.cornerPoints(), d.score)
                val id = "box-${n + 1}"
                vertexes[id] = ArchiveBox(seg)
                boxQuads += id to seg.points
            }

        for (d in dets.filter { it.label == STICKER_CLASS }) {
            val seg = Boundary(d.polygon.cornerPoints(), d.score)
            val center = seg.points.centroid
            // Boxes are in confidence order, so a sticker overlapping two boxes goes to the more
            // confident one
            boxQuads
                .firstOrNull { it.second.containsPoint(center) }
                ?.let { box ->
                    vertexes[box.first] =
                        vertexes[box.first].let {
                            it!!.copy(
                                labelBoundaries = listOf(*it.labelBoundaries.toTypedArray(), seg)
                            )
                        }
                }
        }

        return imagePath to JobFile(vertexes)
    }

    /** Intersection over union of two polygons' axis-aligned bounding boxes. */
    private fun iou(a: Polygon, b: Polygon): Double {
        val w = (min(a.xMax, b.xMax) - max(a.xMin, b.xMin)).coerceAtLeast(0)
        val h = (min(a.yMax, b.yMax) - max(a.yMin, b.yMin)).coerceAtLeast(0)
        val inter = w.toDouble() * h
        fun area(p: Polygon) = (p.xMax - p.xMin).toDouble() * (p.yMax - p.yMin)
        return inter / (area(a) + area(b) - inter)
    }

    /**
     * Greedy non-maximum suppression: of any same-class detections overlapping by more than
     * [iouThreshold], only the most confident is kept. The end2end model skips NMS, so duplicates
     * can otherwise get through.
     */
    private fun List<Detection>.suppressOverlaps(iouThreshold: Double = 0.5): List<Detection> =
        sortedByDescending { it.score }
            .fold(listOf()) { kept, d ->
                if (kept.any { it.label == d.label && iou(it.polygon, d.polygon) > iouThreshold })
                    kept
                else kept + d
            }

    /** Loads an ONNX model once per path, with the class names and input size stored at export. */
    private fun loadModel(modelPath: Path): Model =
        models.getOrPut(modelPath.pathString) {
            val session = env.createSession(modelPath.pathString, OrtSession.SessionOptions())
            val meta = session.metadata.customMetadata
            // Ultralytics stores these as Python reprs: "{0: 'Box', 1: 'Sticker'}" and "[640, 640]"
            val names =
                Regex("""(\d+)\s*:\s*['"]([^'"]*)['"]""")
                    .findAll(meta.getValue("names"))
                    .associate { it.groupValues[1].toInt() to it.groupValues[2] }
            val (inH, inW) =
                Regex("""\d+""").findAll(meta.getValue("imgsz")).map { it.value.toInt() }.toList()
            Model(session, names, inH, inW)
        }

    private class Letterboxed(
        val padded: Mat,
        val r: Double,
        val left: Int,
        val top: Int,
        val newW: Int,
        val newH: Int,
    )

    /**
     * Resizes an image to fit the model input while keeping aspect ratio, padding the rest grey.
     */
    private fun letterbox(image: Mat, inH: Int, inW: Int): Letterboxed {
        val h = image.rows()
        val w = image.cols()
        val r = min(inH.toDouble() / h, inW.toDouble() / w)

        val newW = round(w * r).toInt()
        val newH = round(h * r).toInt()
        val padX = (inW - newW) / 2.0
        val padY = (inH - newH) / 2.0
        val top = round(padY - 0.1).toInt()
        val bottom = round(padY + 0.1).toInt()
        val left = round(padX - 0.1).toInt()
        val right = round(padX + 0.1).toInt()

        val resized = CV.resize(image, newW to newH, interp = INTER_LINEAR)
        val padded = CV.border(resized, top, bottom, left, right, MonoColour(114u))
        resized.release()

        return Letterboxed(padded, r, left, top, newW, newH)
    }

    /** Runs the ONNX model on one image and decodes each detection into a mask outline. */
    private fun runModel(
        imagePath: JobRelativePath,
        modelPath: Path,
        conf: Double,
        edgeMargin: Double,
    ): List<Detection> {
        val model = loadModel(modelPath)
        val inH = model.inH
        val inW = model.inW

        val image = CV.readImage(imagePath.absolute)
        if (image.empty()) throw FileNotFoundException("Could not read image: $imagePath")
        val h = image.rows()
        val w = image.cols()

        // BGR -> RGB, HWC -> NCHW, scaled to 0-1 as in training
        val lb = letterbox(image, inH, inW)
        image.release()
        val rgb = CV.bgr2rgb(lb.padded)
        rgb.convertTo(rgb, CV_32FC3, 1.0 / 255.0, 0.0)
        val hwc = rgb.run {
            val src = if (isContinuous) this else clone()
            val out = FloatArray((src.total() * src.channels()).toInt())
            FloatPointer(src.data()).get(out)
            out
        }
        rgb.release()
        lb.padded.release()

        val chw = FloatArray(hwc.size)
        val plane = inH * inW
        for (i in 0 until plane) for (ch in 0 until 3) chw[ch * plane + i] = hwc[i * 3 + ch]

        val inputName = model.session.inputNames.first()
        val (predsShape, preds, protosShape, protos) =
            OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(chw),
                    longArrayOf(1, 3, inH.toLong(), inW.toLong()),
                )
                .use { input ->
                    model.session.run(mapOf(inputName to input)).use { result ->
                        // Detections are (1, N, 6 + 32) and mask prototypes are (1, 32, H, W), so
                        // tell them apart by rank
                        val outs =
                            result.map { it.value as OnnxTensor }.sortedBy { it.info.shape.size }
                        fun data(t: OnnxTensor) =
                            t.floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                        Outputs(
                            outs[0].info.shape,
                            data(outs[0]),
                            outs[1].info.shape,
                            data(outs[1]),
                        )
                    }
                }

        val c = protosShape[1].toInt()
        val mh = protosShape[2].toInt()
        val mw = protosShape[3].toInt()
        val n = predsShape[1].toInt()
        val stride = predsShape[2].toInt()
        require(stride == 6 + c) {
            "Unexpected output shape ${predsShape.toList()}; export the model with end2end=True"
        }

        val out = mutableListOf<Detection>()
        for (k in 0 until n) {
            val o = k * stride
            val score = preds[o + 4]
            if (score < conf) continue

            // Box from letterbox space back into original image pixels
            val x1 = ((preds[o] - lb.left) / lb.r).coerceIn(0.0, w.toDouble())
            val x2 = ((preds[o + 2] - lb.left) / lb.r).coerceIn(0.0, w.toDouble())
            val y1 = ((preds[o + 1] - lb.top) / lb.r).coerceIn(0.0, h.toDouble())
            val y2 = ((preds[o + 3] - lb.top) / lb.r).coerceIn(0.0, h.toDouble())

            // The box is clamped to the image, so a box cut off by a side is centred near it
            val label = model.names.getValue(preds[o + 5].toInt())
            val cx = (x1 + x2) / 2 / w
            val cy = (y1 + y2) / 2 / h
            if (label == BOX_CLASS && listOf(cx, cy).any { it < edgeMargin || it > 1 - edgeMargin })
                continue

            // Mask logits are the detection's coefficients applied to the shared prototypes;
            // upsampled to full image resolution, equivalent to retina_masks=True
            val logits = FloatArray(mh * mw)
            for (j in 0 until c) {
                val coef = preds[o + 6 + j]
                val base = j * mh * mw
                for (p in logits.indices) logits[p] += coef * protos[base + p]
            }
            val small = Mat(mh, mw, CV_32F).also { FloatPointer(it.data()).put(*logits) }
            val modelRes = CV.resize(small, inW to inH, interp = INTER_LINEAR)
            val full =
                CV.resize(
                    modelRes.subMat(Rectangle.create(lb.left, lb.top, lb.newW, lb.newH)),
                    w to h,
                    interp = INTER_LINEAR,
                )

            // logit > 0 is sigmoid > 0.5; masks are cropped to their own box as Ultralytics does
            val ix1 = x1.toInt()
            val iy1 = y1.toInt()
            val ix2 = ceil(x2).toInt()
            val iy2 = ceil(y2).toInt()
            val mask = Mat.zeros(h, w, CV_8U).asMat()
            if (ix2 > ix1 && iy2 > iy1) {
                val roi = Rectangle.create(ix1, iy1, ix2 - ix1, iy2 - iy1)
                val bin = CV.threshold(full.subMat(roi), 0.0, 255.0, THRESH_BINARY)
                bin.convertTo(mask.subMat(roi), CV_8U)
                bin.release()
            }
            small.release()
            modelRes.release()
            full.release()

            val contours = CV.contours(mask, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)

            if (contours.points.isEmpty()) continue

            mask.release()

            // Largest contour only, so stray specks of mask don't drag the convex hull around
            val largest = contours.largest
            out +=
                Detection(
                    label,
                    score.toDouble(),
                    Polygon.create(largest),
                )
        }
        return out
    }

    // Copied out of the ONNX tensors so they can be closed before decoding
    private data class Outputs(
        val predsShape: LongArray,
        val preds: FloatArray,
        val protosShape: LongArray,
        val protos: FloatArray,
    )

    /** Gets corners of a (not axis-aligned) polygon by simplifying its convex hull to 4 points. */
    private fun Polygon.cornerPoints(): Quad {
        val hull = convexHull()
        val perimeter = CV.perimeter(hull.vertices.toList(), true)

        // Smallest epsilon that gives a quadrilateral; fewer than 4 points means DP has failed
        for (i in 0..10) {
            val epsilon = 0.01 + i * 0.01
            val approx = CV.approxPoly(hull.vertices.toList(), epsilon * perimeter, true)
            if (approx.size == 4) return Quad.create(approx).toClockwise()
        }

        // Fall back to the minimum-area rectangle
        return hull.bestBoundingBox()
    }
}
