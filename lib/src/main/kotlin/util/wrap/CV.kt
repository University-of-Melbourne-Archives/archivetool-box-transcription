package au.edu.unimelb.archivetool.util.wrap

import au.edu.unimelb.archivetool.switchboard.jobstate.GENERATED_PATH
import au.edu.unimelb.archivetool.switchboard.jobstate.JobRelativePath
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.file.Image.FileType
import au.edu.unimelb.archivetool.util.math.geometry.Point as Pt
import au.edu.unimelb.archivetool.util.math.geometry.Point.Companion.flatten
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.roundToInt
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.javacpp.indexer.IntIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_imgcodecs
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatExpr
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Size

object CV {
    data class ColourSpace(val depth: DepthType, val channels: Int) {
        enum class DepthType {
            U8,
            I8,
            U16,
            I16,
            I32,
            F32,
            F64,
        }

        companion object {
            fun fromMatType(colorSpace: Int): ColourSpace {
                val nDepth: Int = colorSpace and opencv_core.CV_MAT_DEPTH_MASK
                val channels: Int = 1 + (colorSpace shr opencv_core.CV_CN_SHIFT)
                val depth = DepthType.entries[nDepth]
                return ColourSpace(depth, channels)
            }
        }
    }

    typealias Point = org.bytedeco.opencv.opencv_core.Point

    var loaded = false

    fun clahe(_in: Mat, tilesGridSize: Pair<Int, Int>, clipLimit: Double): Mat {
        val _out = Mat()
        val algorithm = opencv_imgproc.createCLAHE()
        algorithm.tilesGridSize = Size(tilesGridSize.first, tilesGridSize.second)
        algorithm.clipLimit = clipLimit
        algorithm.apply(_in, _out)
        return _out
    }

    val formatRequirements by lazy {
        object : Image.FileType.Supported {
            override val supportedFormats by lazy {
                mapOf(
                    FileType.WindowsBitmap to null,
                    FileType.GIF to null,
                    FileType.JPEG to null,
                    FileType.JPEG2K to null,
                    FileType.PNG to null,
                    FileType.WebP to null,
                    // This might support other HEIF variants but it is difficult to tell.
                    FileType.AVIF to null,
                    FileType.PNM to null,
                    FileType.SunRaster to null,
                    FileType.TIFF to null,
                    FileType.OpenEXR to null,
                    FileType.RadianceHDR to null,
                    FileType.GeospatialDAL to null,
                )
            }
        }
    }

    fun bgr2grayc1(_in: Mat): Mat {
        val _out = Mat()
        opencv_imgproc.cvtColor(
            _in,
            _out,
            opencv_imgproc.COLOR_BGR2GRAY,
            1,
            opencv_core.ALGO_HINT_DEFAULT,
        )
        return _out
    }

    fun bgr2rgb(_in: Mat): Mat {
        val _out = Mat()
        opencv_imgproc.cvtColor(_in, _out, opencv_imgproc.COLOR_BGR2RGB)
        return _out
    }

    fun fillQuad(img: Mat, quad: Quad, colour: Colour) {
        val pointPtr = Point(IntPointer(*quad.toClockwise().vertices.flatten().toIntArray()))

        opencv_imgproc.fillConvexPoly(
            img,
            pointPtr,
            4, // Quads already assert their size.
            colour.rgb().toCVBGRScalar(),
        )
    }

    fun gaussian(
        _in: Mat,
        kernelSize: Pair<Int, Int>,
        sigmaX: Double,
        sigmaY: Double = 0.0,
        borderStrategy: Int = BORDER_DEFAULT,
        hint: Int = ALGO_HINT_DEFAULT,
    ): Mat {
        val _out = Mat()
        val kernelSize = Size(kernelSize.first, kernelSize.second)
        GaussianBlur(_in, _out, kernelSize, sigmaX, sigmaY, borderStrategy, hint)
        return _out
    }

    fun divide(numerator: Mat, denominator: Mat, scale: Double, depthType: Int = -1): Mat {
        val out = Mat()
        opencv_core.divide(numerator, denominator, out, scale, depthType)
        return out
    }

    data class PerspectiveTransformMatrix(val mat: Mat, val dSize: Size) : AutoCloseable {
        override fun close() {
            mat.release()
            dSize.releaseReference()
        }

        companion object {
            fun create(bounds: Quad, destinationBounds: Rectangle): PerspectiveTransformMatrix {
                val bounds = bounds.toClockwise()
                val destinationBounds = destinationBounds.toClockwise()

                val srcCorners = FloatPointer(*bounds.floatArray)

                val dstCorners = FloatPointer(*destinationBounds.floatArray)

                val src = Mat(Size(2, 4), opencv_core.CV_32F, srcCorners)
                val dst = Mat(Size(2, 4), opencv_core.CV_32F, dstCorners)

                return PerspectiveTransformMatrix(
                        opencv_imgproc.getPerspectiveTransform(src, dst),
                        destinationBounds.getCVSize(),
                    )
                    .also {
                        src.release()
                        dst.release()
                    }
            }
        }
    }

    @Deprecated("Prefer variant with PerspectiveTransformMatrix")
    fun perspectiveWarp(sourceImage: Mat, bounds: Quad, destinationBounds: Rectangle): Mat {
        val result = Mat()
        PerspectiveTransformMatrix.create(bounds, destinationBounds).use {
            opencv_imgproc.warpPerspective(sourceImage, result, it.mat, it.dSize)
        }

        return result
    }

    fun perspectiveWarp(sourceImage: Mat, m: PerspectiveTransformMatrix): Mat {
        val result = Mat()
        opencv_imgproc.warpPerspective(sourceImage, result, m.mat, m.dSize)

        return result
    }

    fun perspectiveTransformRel(m: PerspectiveTransformMatrix, vararg points: Point): List<Point> {
        val src = Mat(points.size, 1, opencv_core.CV_64FC2)
        val idxr = src.createIndexer<DoubleIndexer>()
        points.forEachIndexed { i, p ->
            idxr.put(i.toLong(), 0, 0, p.x().toDouble())
            idxr.put(i.toLong(), 0, 1, p.y().toDouble())
        }

        val result = Mat()

        opencv_core.perspectiveTransform(src, result, m.mat)

        val resultIdxr: DoubleIndexer = result.createIndexer()
        return (0 until points.size).map { i ->
            val x = resultIdxr.get(i.toLong(), 0, 0)
            val y = resultIdxr.get(i.toLong(), 0, 1)
            Point(x.roundToInt(), y.roundToInt())
        }
    }

    // Because varargs doesn't work, I need an overload.
    fun perspectiveTransformRel(
        m: PerspectiveTransformMatrix,
        point: au.edu.unimelb.archivetool.util.math.geometry.Point,
    ): List<au.edu.unimelb.archivetool.util.math.geometry.Point> = perspectiveTransformRel(m, point)

    fun perspectiveTransformRel(
        m: PerspectiveTransformMatrix,
        points: Array<out au.edu.unimelb.archivetool.util.math.geometry.Point>,
    ): List<au.edu.unimelb.archivetool.util.math.geometry.Point> =
        perspectiveTransformRel(m, points.asIterable())

    fun <I : Iterable<au.edu.unimelb.archivetool.util.math.geometry.Point>> perspectiveTransformRel(
        m: PerspectiveTransformMatrix,
        points: I,
    ): List<au.edu.unimelb.archivetool.util.math.geometry.Point> {
        val n = points.count()
        val src = Mat(n, 1, opencv_core.CV_64FC2)
        val idxr = src.createIndexer<DoubleIndexer>()
        points.forEachIndexed { i, p ->
            idxr.put(i.toLong(), 0, 0, p.x.toDouble())
            idxr.put(i.toLong(), 0, 1, p.y.toDouble())
        }

        val result = Mat()

        opencv_core.perspectiveTransform(src, result, m.mat)

        val resultIdxr: DoubleIndexer = result.createIndexer()
        return (0 until n).map { i ->
            val x = resultIdxr.get(i.toLong(), 0, 0)
            val y = resultIdxr.get(i.toLong(), 0, 1)
            au.edu.unimelb.archivetool.util.math.geometry.Point.create(
                x.roundToInt(),
                y.roundToInt(),
            )
        }
    }

    fun polyPerspectiveTransformRel(poly: Polygon, m: PerspectiveTransformMatrix): Polygon {
        val points = poly.vertices

        val vertices = perspectiveTransformRel(m, points)

        return Polygon.create(vertices)
    }

    fun resize(
        src: Mat,
        targetDims: Pair<Int, Int>? = null,
        factors: Pair<Double, Double> = 0.0 to 0.0,
        interp: Int = INTER_LINEAR,
    ): Mat {
        val result = Mat()

        val dsize = targetDims?.let { (w, h) -> Size(w, h) } ?: Size()
        val (fx, fy) = factors

        opencv_imgproc.resize(src, result, dsize, fx, fy, interp)

        return result
    }

    fun border(
        src: Mat,
        top: Int,
        bottom: Int,
        left: Int,
        right: Int,
        colour: Colour,
        borderType: Int = BORDER_CONSTANT,
    ): Mat {
        val dst = Mat()
        copyMakeBorder(src, dst, top, bottom, left, right, borderType, colour.rgb().toCVBGRScalar())
        return dst
    }

    fun threshold(src: Mat, threshold: Double, max: Double, type: Int): Mat {
        val dst = Mat()
        threshold(src, dst, threshold, max, type)
        return dst
    }

    data class Contours(val points: List<List<Pt>>, val nodes: List<Node>) {
        internal constructor(
            contours: MatVector,
            hierarchy: Mat,
        ) : this(
            (0 until contours.size()).map { i ->
                val mat = contours.get(i)
                mat.toPointList()
            },
            listOf<Node>().takeIf { hierarchy.empty() }
                ?: hierarchy.createIndexer<IntIndexer>().use { idx ->
                    List(hierarchy.cols()) { i ->
                        fun v(k: Long): Int? = idx.get(0L, i.toLong(), k).takeIf { it >= 0 }
                        Node(i, v(0), v(1), v(2), v(3))
                    }
                },
        )

        val areas by lazy { points.map { it to contourArea(it.toMat()) } }

        val largest by lazy { areas.maxBy { it.second }.first }

        data class Node(
            val index: Int,
            val next: Int?, // next contour at the same level
            val previous: Int?, // previous contour at the same level
            val firstChild: Int?, // first contour nested inside this one
            val parent: Int?, // contour that contains this one
        )
    }

    fun List<Pt>.toMat(): Mat {
        val mat = Mat(size, 1, CV_32SC2)
        mat.createIndexer<IntIndexer>().use { idx ->
            forEachIndexed { r, p ->
                idx.put(r.toLong(), 0L, 0L, p.x)
                idx.put(r.toLong(), 0L, 1L, p.y)
            }
        }
        return mat
    }

    fun Mat.toPointList(): List<Pt> =
        createIndexer<IntIndexer>().use { idx ->
            List(rows()) { r -> Pt.create(idx.get(r.toLong(), 0, 0), idx.get(r.toLong(), 0, 1)) }
        }

    fun MatVector.release() =
        (0 until size()).forEach { i -> get(i).release() }.also { releaseReference() }

    fun contours(src: Mat, mode: Int, method: Int, offset: Pt? = null): Contours {
        val contours = MatVector()
        val hierarchy = Mat()
        if (offset != null) {
            findContours(
                src,
                contours,
                hierarchy,
                RETR_EXTERNAL,
                CHAIN_APPROX_SIMPLE,
                offset.toCV(),
            )
        } else {
            findContours(src, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)
        }

        return Contours(contours, hierarchy).also {
            contours.release()
            hierarchy.release()
        }
    }

    fun convexHull(points: List<Pt>, clockwise: Boolean = true): List<Pt> {
        val out = Mat()
        val pointMat = points.toMat()
        convexHull(pointMat, out, clockwise, true)

        return out.toPointList()
    }

    fun perimeter(poly: List<Pt>, closed: Boolean) = arcLength(poly.toMat(), closed)

    fun approxPoly(curve: List<Pt>, epsilon: Double, closed: Boolean = true) =
        approxPolyDouglasPecker(curve, epsilon, closed)

    fun approxPolyDouglasPecker(
        curve: List<Pt>,
        epsilon: Double,
        closed: Boolean = true,
    ): List<Pt> {
        val out = Mat()
        approxPolyDP(curve.toMat(), out, epsilon, closed)
        return out.toPointList()
    }

    fun Mat.subMat(rect: Rectangle): Mat = apply(rect.toCVRect())

    fun MatExpr.subMat(rect: Rectangle): MatExpr = apply(rect.toCVRect())

    fun readImage(path: Path): Mat = opencv_imgcodecs.imread(path.pathString)

    fun canReadImage(path: Path): Boolean = opencv_imgcodecs.haveImageReader(path.pathString)

    fun Mat.writeImage(path: Path) {
        path.createParentDirectories()
        opencv_imgcodecs.imwrite(path.pathString, this)
    }

    // Takes a path to an image, converts it to a format via extension and returns the converted
    // file path.
    fun convert(path: JobRelativePath, extension: String): JobRelativePath =
        JobRelativePath(
                path.rootDirectory,
                Path(GENERATED_PATH)
                    .resolve("conv")
                    .resolve(path.relative)
                    .resolveSibling("${path.relative.name}.${extension}"),
            )
            .also {
                if (it.absolute.notExists()) {
                    readImage(path.absolute).writeImage(it.absolute)
                }
            }

    // Takes a path to an image, converts it to a format and returns the converted file path.
    fun convert(path: JobRelativePath, target: Image.FileType): JobRelativePath =
        convert(path, target.extensions.first())
}
