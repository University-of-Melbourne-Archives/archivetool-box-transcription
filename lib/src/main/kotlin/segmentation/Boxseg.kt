package au.edu.unimelb.archivetool.segmentation



import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import nu.pattern.OpenCV
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.min

const val BOX_CLASS = "Box"
const val STICKER_CLASS = "Sticker"
val DEFAULT_MODEL_PATH: String =
    File("lib/src/main/python/model-sandbox/weights/yolo26n-archive-boxes-14-09.onnx").path

// Output types; add @Serializable (kotlinx.serialization) to get the same JSON shape as the Python dict
data class Pt(val x: Double, val y: Double)
data class Segment(val points: List<Pt>, val confidence: Double, val mask: List<Pt>? = null)
data class BoxEntry(val box: Segment, val sticker: MutableList<Segment> = mutableListOf())
data class ImageDetections(val vertexes: Map<String, BoxEntry>)

private class Detection(val label: String, val score: Double, val polygon: List<Point>)
private class Model(val session: OrtSession, val names: Map<Int, String>, val inH: Int, val inW: Int)

private val env: OrtEnvironment by lazy { OpenCV.loadLocally(); OrtEnvironment.getEnvironment() }
private val models = ConcurrentHashMap<String, Model>()

/**
 * Segments an image into Box & Sticker polygons and returns detections as quadrilaterals,
 * keyed by image file name.
 */
fun getBoxes(
    imagePath: String,
    modelPath: String = DEFAULT_MODEL_PATH,
    conf: Float = 0.5f,
    includeMask: Boolean = false,
): Map<String, ImageDetections> {
    val dets = runModel(imagePath, modelPath, conf).filter { it.polygon.size >= 3 }
    val vertexes = linkedMapOf<String, BoxEntry>()

    // Key every box as box-N in confidence order
    val boxQuads = mutableListOf<Pair<String, List<Point>>>()
    dets.filter { it.label == BOX_CLASS }.sortedByDescending { it.score }.forEachIndexed { n, d ->
        val (seg, quad) = toSegment(d.polygon, d.score, includeMask)
        val id = "box-${n + 1}"
        vertexes[id] = BoxEntry(seg)
        boxQuads += id to quad
    }

    for (d in dets.filter { it.label == STICKER_CLASS }) {
        val (seg, quad) = toSegment(d.polygon, d.score, includeMask)
        val centre = Point(quad.map { it.x }.average(), quad.map { it.y }.average())
        // Boxes are in confidence order, so a sticker overlapping two boxes goes to the more confident one
        boxQuads.firstOrNull { insideQuad(centre, it.second) }?.let { vertexes[it.first]!!.sticker += seg }
    }

    return mapOf(File(imagePath).name to ImageDetections(vertexes))
}

/** Loads an ONNX model once per path, with the class names and input size stored at export. */
private fun loadModel(modelPath: String): Model = models.getOrPut(modelPath) {
    val session = env.createSession(modelPath, OrtSession.SessionOptions())
    val meta = session.metadata.customMetadata
    // Ultralytics stores these as Python reprs: "{0: 'Box', 1: 'Sticker'}" and "[640, 640]"
    val names = Regex("""(\d+)\s*:\s*['"]([^'"]*)['"]""").findAll(meta.getValue("names"))
        .associate { it.groupValues[1].toInt() to it.groupValues[2] }
    val (inH, inW) = Regex("""\d+""").findAll(meta.getValue("imgsz")).map { it.value.toInt() }.toList()
    Model(session, names, inH, inW)
}

private class Letterboxed(val padded: Mat, val r: Double, val left: Int, val top: Int, val newW: Int, val newH: Int)

/** Resizes an image to fit the model input while keeping aspect ratio, padding the rest grey. */
private fun letterbox(image: Mat, inH: Int, inW: Int): Letterboxed {
    val h = image.rows(); val w = image.cols()
    val r = min(inH.toDouble() / h, inW.toDouble() / w)
    // Math.rint is round-half-even like Python's round, matching Ultralytics' LetterBox
    val newW = Math.rint(w * r).toInt(); val newH = Math.rint(h * r).toInt()
    val padX = (inW - newW) / 2.0; val padY = (inH - newH) / 2.0
    val top = Math.rint(padY - 0.1).toInt(); val bottom = Math.rint(padY + 0.1).toInt()
    val left = Math.rint(padX - 0.1).toInt(); val right = Math.rint(padX + 0.1).toInt()

    val resized = Mat()
    Imgproc.resize(image, resized, Size(newW.toDouble(), newH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
    val padded = Mat()
    Core.copyMakeBorder(resized, padded, top, bottom, left, right, Core.BORDER_CONSTANT, Scalar(114.0, 114.0, 114.0))
    resized.release()
    return Letterboxed(padded, r, left, top, newW, newH)
}

/** Runs the ONNX model on one image and decodes each detection into a mask outline. */
private fun runModel(imagePath: String, modelPath: String, conf: Float): List<Detection> {
    val model = loadModel(modelPath)
    val inH = model.inH; val inW = model.inW

    val image = Imgcodecs.imread(imagePath)
    if (image.empty()) throw FileNotFoundException("Could not read image: $imagePath")
    val h = image.rows(); val w = image.cols()

    // BGR -> RGB, HWC -> NCHW, scaled to 0-1 as in training
    val lb = letterbox(image, inH, inW)
    image.release()
    val rgb = Mat()
    Imgproc.cvtColor(lb.padded, rgb, Imgproc.COLOR_BGR2RGB)
    rgb.convertTo(rgb, CvType.CV_32FC3, 1.0 / 255.0)
    val hwc = FloatArray(inH * inW * 3).also { rgb.get(0, 0, it) }
    rgb.release(); lb.padded.release()
    val chw = FloatArray(hwc.size)
    val plane = inH * inW
    for (i in 0 until plane) for (ch in 0 until 3) chw[ch * plane + i] = hwc[i * 3 + ch]

    val inputName = model.session.inputNames.first()
    val (predsShape, preds, protosShape, protos) =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, inH.toLong(), inW.toLong())).use { input ->
            model.session.run(mapOf(inputName to input)).use { result ->
                // Detections are (1, N, 6 + 32) and mask prototypes are (1, 32, H, W), so tell them apart by rank
                val outs = result.map { it.value as OnnxTensor }.sortedBy { it.info.shape.size }
                fun data(t: OnnxTensor) = t.floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                Outputs(outs[0].info.shape, data(outs[0]), outs[1].info.shape, data(outs[1]))
            }
        }

    val c = protosShape[1].toInt(); val mh = protosShape[2].toInt(); val mw = protosShape[3].toInt()
    val n = predsShape[1].toInt(); val stride = predsShape[2].toInt()
    require(stride == 6 + c) { "Unexpected output shape ${predsShape.toList()}; export the model with end2end=True" }

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

        // Mask logits are the detection's coefficients applied to the shared prototypes;
        // upsampled to full image resolution, equivalent to retina_masks=True
        val logits = FloatArray(mh * mw)
        for (j in 0 until c) {
            val coef = preds[o + 6 + j]
            val base = j * mh * mw
            for (p in logits.indices) logits[p] += coef * protos[base + p]
        }
        val small = Mat(mh, mw, CvType.CV_32F).apply { put(0, 0, logits) }
        val modelRes = Mat()
        Imgproc.resize(small, modelRes, Size(inW.toDouble(), inH.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val full = Mat()
        Imgproc.resize(modelRes.submat(Rect(lb.left, lb.top, lb.newW, lb.newH)), full,
            Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

        // logit > 0 is sigmoid > 0.5; masks are cropped to their own box as Ultralytics does
        val ix1 = x1.toInt(); val iy1 = y1.toInt()
        val ix2 = ceil(x2).toInt(); val iy2 = ceil(y2).toInt()
        val mask = Mat.zeros(h, w, CvType.CV_8U)
        if (ix2 > ix1 && iy2 > iy1) {
            val roi = Rect(ix1, iy1, ix2 - ix1, iy2 - iy1)
            val bin = Mat()
            Imgproc.threshold(full.submat(roi), bin, 0.0, 255.0, Imgproc.THRESH_BINARY)
            bin.convertTo(mask.submat(roi), CvType.CV_8U)
            bin.release()
        }
        small.release(); modelRes.release(); full.release()

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        mask.release(); hierarchy.release()
        if (contours.isEmpty()) continue

        // Largest contour only, so stray specks of mask don't drag the convex hull around
        val largest = contours.maxBy { Imgproc.contourArea(it) }
        out += Detection(model.names.getValue(preds[o + 5].toInt()), score.toDouble(), largest.toList())
        contours.forEach { it.release() }
    }
    return out
}

// Copied out of the ONNX tensors so they can be closed before decoding
private data class Outputs(val predsShape: LongArray, val preds: FloatArray, val protosShape: LongArray, val protos: FloatArray)

/** Orders 4 points starting from the top-left and moving clockwise (on screen, since image y points down). */
private fun orderCorners(quad: List<Point>): List<Point> {
    val cx = quad.map { it.x }.average(); val cy = quad.map { it.y }.average()
    val sorted = quad.sortedBy { atan2(it.y - cy, it.x - cx) }
    val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
    return sorted.drop(start) + sorted.take(start)
}

/** Gets corners of a (not axis-aligned) polygon by simplifying its convex hull to 4 points. */
private fun getCornerPoints(polygon: List<Point>): List<Point> {
    val idx = MatOfInt()
    Imgproc.convexHull(MatOfPoint(*polygon.toTypedArray()), idx)
    val hull = MatOfPoint2f(*idx.toArray().map { polygon[it] }.toTypedArray())
    val perimeter = Imgproc.arcLength(hull, true)

    // Smallest epsilon that gives a quadrilateral; fewer than 4 points means DP has failed
    for (i in 0 until 10) {
        val epsilon = 0.01 + i * 0.01
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(hull, approx, epsilon * perimeter, true)
        val pts = approx.toList()
        if (pts.size == 4) return orderCorners(pts)
        if (pts.size < 4) break
    }

    // Fall back to the minimum-area rectangle
    val box = Mat()
    Imgproc.boxPoints(Imgproc.minAreaRect(hull), box)
    return orderCorners((0 until 4).map { Point(box.get(it, 0)[0], box.get(it, 1)[0]) })
}

private fun round1(v: Double) = Math.round(v * 10) / 10.0
private fun toPoints(quad: List<Point>) = quad.map { Pt(round1(it.x), round1(it.y)) }

/** Checks whether a point lies inside a convex quadrilateral (same side of every edge). */
private fun insideQuad(point: Point, quad: List<Point>): Boolean {
    val crosses = quad.indices.map { i ->
        val a = quad[i]; val b = quad[(i + 1) % quad.size]
        (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
    }
    return crosses.all { it >= 0 } || crosses.all { it <= 0 }
}

/** Formats a polygon and score as a Segment, returning the unrounded quad for containment tests. */
private fun toSegment(polygon: List<Point>, score: Double, includeMask: Boolean): Pair<Segment, List<Point>> {
    val quad = getCornerPoints(polygon)
    // Mask outline is debug only, for comparing against a misdrawn quad
    return Segment(toPoints(quad), score, if (includeMask) toPoints(polygon) else null) to quad
}

/** Runs getBoxes over every image in a directory. */
fun getBoxesForDirectory(dir: String, modelPath: String = DEFAULT_MODEL_PATH, conf: Float = 0.5f) =
    File(dir).listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "tif", "tiff", "bmp") }!!
        .sortedBy { it.name }
        .fold(linkedMapOf<String, ImageDetections>()) { acc, f -> acc.apply { putAll(getBoxes(f.path, modelPath, conf)) } }

/** just for testing **/
fun main() {
    val image = File("lib/src/test/resources/SwitchboardTest/PXL_20251127_230150462.jpg")
    val model = File(DEFAULT_MODEL_PATH)

    // Relative paths resolve against the working directory, so print it if a file can't be found
    println("Working dir: ${File("").absolutePath}")
    require(image.exists()) { "Image not found: ${image.absolutePath}" }
    require(model.exists()) { "Model not found: ${model.absolutePath}" }

    val start = System.currentTimeMillis()
    val result = getBoxes(image.path)
    println("Inference took ${System.currentTimeMillis() - start} ms")

    for ((name, det) in result) {
        println(name)
        for ((id, entry) in det.vertexes) {
            println("  $id  conf=%.3f  %s".format(entry.box.confidence, entry.box.points.joinToString { "(${it.x}, ${it.y})" }))
            for (s in entry.sticker) {
                println("    sticker  conf=%.3f  %s".format(s.confidence, s.points.joinToString { "(${it.x}, ${it.y})" }))
            }
        }
    }
}