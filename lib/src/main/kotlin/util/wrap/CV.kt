package au.edu.unimelb.archivetool.util.wrap

import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.file.Image.FileType
import au.edu.unimelb.archivetool.util.math.geometry.Point.Companion.flatten
import au.edu.unimelb.archivetool.util.math.geometry.Polygon
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.math.geometry.Rectangle
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import kotlin.math.roundToInt
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.indexer.DoubleIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_core.ALGO_HINT_DEFAULT
import org.bytedeco.opencv.global.opencv_core.BORDER_DEFAULT
import org.bytedeco.opencv.global.opencv_imgcodecs
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur
import org.bytedeco.opencv.opencv_core.Mat
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

    fun readImage(path: Path): Mat = opencv_imgcodecs.imread(path.pathString)

    fun canReadImage(path: Path): Boolean = opencv_imgcodecs.haveImageReader(path.pathString)

    fun Mat.writeImage(path: Path) {
        path.createParentDirectories()
        opencv_imgcodecs.imwrite(path.pathString, this)
    }
}
