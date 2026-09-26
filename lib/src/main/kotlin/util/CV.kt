package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.geometry.Point as GeoPoint
import au.edu.unimelb.archivetool.util.geometry.Point.Companion.flatten
import au.edu.unimelb.archivetool.util.geometry.Polygon
import au.edu.unimelb.archivetool.util.geometry.Quad
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import kotlin.math.roundToInt
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_core.ALGO_HINT_DEFAULT
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_64FC2
import org.bytedeco.opencv.global.opencv_core.CV_CN_SHIFT
import org.bytedeco.opencv.global.opencv_core.CV_MAT_DEPTH_MASK
import org.bytedeco.opencv.global.opencv_core.perspectiveTransform
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point as CVPoint
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
                val nDepth: Int = colorSpace and CV_MAT_DEPTH_MASK
                val channels: Int = 1 + (colorSpace shr CV_CN_SHIFT)
                val depth = DepthType.entries[nDepth]
                return ColourSpace(depth, channels)
            }
        }
    }

    typealias Point = CVPoint

    var loaded = false

    // NOTE: this consumes _in I believe.
    fun clahe(_in: Mat, tilesGridSize: Pair<Int, Int>, clipLimit: Double): Mat {
        val _out = Mat()
        val algorithm = createCLAHE()
        algorithm.tilesGridSize = Size(tilesGridSize.first, tilesGridSize.second)
        algorithm.clipLimit = clipLimit
        algorithm.apply(_in, _out)
        return _out
    }

    fun bgr2grayc1(_in: Mat): Mat {
        val _out = Mat()
        cvtColor(_in, _out, COLOR_BGR2GRAY, 1, ALGO_HINT_DEFAULT)
        return _out
    }

    fun fillQuad(img: Mat, quad: Quad, colour: Colour) {
        val pointPtr = CVPoint(IntPointer(*quad.toClockwise().vertices.flatten().toIntArray()))

        fillConvexPoly(
            img,
            pointPtr,
            4, // Quads already assert their size.
            colour.rgb().toCVBGRScalar(),
        )
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

                val src = Mat(Size(2, 4), CV_32F, srcCorners)
                val dst = Mat(Size(2, 4), CV_32F, dstCorners)

                return PerspectiveTransformMatrix(
                        getPerspectiveTransform(src, dst),
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
            warpPerspective(sourceImage, result, it.mat, it.dSize)
        }

        return result
    }

    fun perspectiveWarp(sourceImage: Mat, m: PerspectiveTransformMatrix): Mat {
        val result = Mat()
        warpPerspective(sourceImage, result, m.mat, m.dSize)

        return result
    }

    fun perspectiveTransformRel(m: PerspectiveTransformMatrix, vararg points: Point): List<Point> {
        val src = Mat(points.size, 1, CV_64FC2)
        val idxr = src.createIndexer<DoubleIndexer>()
        points.forEachIndexed { i, p ->
            idxr.put(i.toLong(), 0, 0, p.x().toDouble())
            idxr.put(i.toLong(), 0, 1, p.y().toDouble())
        }

        val result = Mat()

        perspectiveTransform(src, result, m.mat)

        val resultIdxr: DoubleIndexer = result.createIndexer()
        return (0 until points.size).map { i ->
            val x = resultIdxr.get(i.toLong(), 0, 0)
            val y = resultIdxr.get(i.toLong(), 0, 1)
            Point(x.roundToInt(), y.roundToInt())
        }
    }

    // Because varargs doesn't work, I need an overload.
    fun perspectiveTransformRel(m: PerspectiveTransformMatrix, point: GeoPoint): List<GeoPoint> = perspectiveTransformRel(m, point)
    fun perspectiveTransformRel(m: PerspectiveTransformMatrix, points: Array<out GeoPoint>): List<GeoPoint> = perspectiveTransformRel(m, points.asIterable())
    fun <I: Iterable<GeoPoint>> perspectiveTransformRel(m: PerspectiveTransformMatrix, points: I): List<GeoPoint> {
        val n = points.count()
        val src = Mat(n, 1, CV_64FC2)
        val idxr = src.createIndexer<DoubleIndexer>()
        points.forEachIndexed { i, p ->
            idxr.put(i.toLong(), 0, 0, p.x.toDouble())
            idxr.put(i.toLong(), 0, 1, p.y.toDouble())
        }

        val result = Mat()

        perspectiveTransform(src, result, m.mat)

        val resultIdxr: DoubleIndexer = result.createIndexer()
        return (0 until n).map { i ->
            val x = resultIdxr.get(i.toLong(), 0, 0)
            val y = resultIdxr.get(i.toLong(), 0, 1)
            GeoPoint.create(x.roundToInt(), y.roundToInt())
        }
    }

    fun polyPerspectiveTransformRel(poly: Polygon, m: PerspectiveTransformMatrix): Polygon {
        val points = poly.vertices

        val vertices = perspectiveTransformRel(m, points)

        return Polygon.create(vertices)
    }

    fun readImage(path: Path): Mat = imread(path.pathString)

    fun Mat.writeImage(path: Path) {
        path.createParentDirectories()
        imwrite(path.pathString, this)
    }
}
