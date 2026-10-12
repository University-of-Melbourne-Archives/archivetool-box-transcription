package au.edu.unimelb.archivetool.segmentation



import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import au.edu.unimelb.archivetool.switchboard.jobstate.ArchiveBox
import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import au.edu.unimelb.archivetool.util.MonoColour
import au.edu.unimelb.archivetool.util.math.geometry.Point
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import au.edu.unimelb.archivetool.util.wrap.CV
import au.edu.unimelb.archivetool.util.wrap.CV.subMat
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_core.CV_8U
import org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE
import org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR
import org.bytedeco.opencv.global.opencv_imgproc.RETR_EXTERNAL
import org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY
import org.bytedeco.opencv.opencv_core.Mat
import java.io.File
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import kotlin.io.path.Path
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round

const val BOX_CLASS = "Box"
const val STICKER_CLASS = "Sticker"
val DEFAULT_MODEL_PATH: String =
    File("lib/src/main/python/model-sandbox/weights/yolo26n-archive-boxes-14-09.onnx").path

private class Detection(val label: String, val score: Double, val polygon: Polygon)
private class Model(val session: OrtSession, val names: Map<Int, String>, val inH: Int, val inW: Int)

private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
private val models: MutableMap<String, Model> by lazy { mutableMapOf() }

/**
 * Segments an image into Box & Sticker polygons and returns detections as quadrilaterals,
 * keyed by image file name.
 */
fun getBoxes(
    imagePath: String,
    modelPath: String = DEFAULT_MODEL_PATH,
    conf: Float = 0.5f,
    includeMask: Boolean = false,
): Map<String, JobFile> {
    val dets = runModel(imagePath, modelPath, conf).filter { it.polygon.vertices.size >= 3 }
    val vertexes = mutableMapOf<String, ArchiveBox>()

    // Key every box as box-N in confidence order
    val boxQuads = mutableListOf<Pair<String, Quad>>()
    dets.filter { it.label == BOX_CLASS }.sortedByDescending { it.score }.forEachIndexed { n, d ->
        val seg = toBoundary(d.polygon, d.score, includeMask)
        val id = "box-${n + 1}"
        vertexes[id] = ArchiveBox(seg)
        boxQuads += id to seg.points
    }

    for (d in dets.filter { it.label == STICKER_CLASS }) {
        val seg = toBoundary(d.polygon, d.score, includeMask)
        val center = seg.points.centroid
        // Boxes are in confidence order, so a sticker overlapping two boxes goes to the more confident one
        boxQuads.firstOrNull { it.second.containsPoint(center) }?.let { box ->
            vertexes[box.first] = vertexes[box.first].let{
                it!!.copy(
                    labelBoundaries = listOf(*it.labelBoundaries.toTypedArray(), seg)
                )
            }
        }
    }

    return mapOf(File(imagePath).name to JobFile(vertexes))
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

    val newW = round(w * r).toInt(); val newH = round(h * r).toInt()
    val padX = (inW - newW) / 2.0; val padY = (inH - newH) / 2.0
    val top = round(padY - 0.1).toInt(); val bottom = round(padY + 0.1).toInt()
    val left = round(padX - 0.1).toInt(); val right = round(padX + 0.1).toInt()

    val resized = CV.resize(image,newW to newH, interp = INTER_LINEAR)
    val padded = CV.border(resized, top, bottom, left, right, MonoColour(114u))
    resized.release()

    return Letterboxed(padded, r, left, top, newW, newH)
}

/** Runs the ONNX model on one image and decodes each detection into a mask outline. */
private fun runModel(imagePath: String, modelPath: String, conf: Float): List<Detection> {
    val model = loadModel(modelPath)
    val inH = model.inH; val inW = model.inW

    val image = CV.readImage(Path(imagePath))
    if (image.empty()) throw FileNotFoundException("Could not read image: $imagePath")
    val h = image.rows(); val w = image.cols()

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
        val small = Mat(mh, mw, CV_32F).also { FloatPointer(it.data()).put(*logits) }
        val modelRes = CV.resize(small, inW to inH, interp = INTER_LINEAR)
        val full = CV.resize(modelRes.subMat(Rectangle.create(lb.left, lb.top, lb.newW, lb.newH)), w to h, interp = INTER_LINEAR)

        // logit > 0 is sigmoid > 0.5; masks are cropped to their own box as Ultralytics does
        val ix1 = x1.toInt(); val iy1 = y1.toInt()
        val ix2 = ceil(x2).toInt(); val iy2 = ceil(y2).toInt()
        val mask = Mat.zeros(h, w, CV_8U).asMat()
        if (ix2 > ix1 && iy2 > iy1) {
            val roi = Rectangle.create(ix1, iy1, ix2 - ix1, iy2 - iy1)
            val bin = CV.threshold(full.subMat(roi), 0.0, 255.0, THRESH_BINARY)
            bin.convertTo(mask.subMat(roi), CV_8U)
            bin.release()
        }
        small.release(); modelRes.release(); full.release()

        val contours = CV.contours(mask, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)

        if (contours.points.isEmpty()) continue

        mask.release()

        // Largest contour only, so stray specks of mask don't drag the convex hull around
        val largest = contours.largest
        out += Detection(model.names.getValue(preds[o + 5].toInt()), score.toDouble(), Polygon.create(largest))
    }
    return out
}

// Copied out of the ONNX tensors so they can be closed before decoding
private data class Outputs(val predsShape: LongArray, val preds: FloatArray, val protosShape: LongArray, val protos: FloatArray)

/** Gets corners of a (not axis-aligned) polygon by simplifying its convex hull to 4 points. */
private fun getCornerPoints(polygon: Polygon): Quad {
    val hull = polygon.convexHull()
    val perimeter = CV.perimeter(hull.vertices.toList(), true)

    // Smallest epsilon that gives a quadrilateral; fewer than 4 points means DP has failed
    for (i in 0 until 10) {
        val epsilon = 0.01 + i * 0.01
        val approx = CV.approxPoly(hull.vertices.toList(), epsilon * perimeter, true)
        if (approx.size == 4) return Quad.create(approx).toClockwise()
    }

    // Fall back to the minimum-area rectangle
    return hull.bestBoundingBox()
}

/** Formats a polygon and score as a Segment, returning the unrounded quad for containment tests. */
private fun toBoundary(polygon: Polygon, score: Double, includeMask: Boolean): Boundary {
    val quad = getCornerPoints(polygon)
    return Boundary(quad, score)
}

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
        for ((id, entry) in det.vertexes!!) {
            println("  $id  conf=%.3f  %s".format(entry.boxBoundary.confidence, entry.boxBoundary.points))
            for (s in entry.labelBoundaries) {
                println("    sticker  conf=%.3f  %s".format(s.confidence, s.points))
            }
        }
    }
}