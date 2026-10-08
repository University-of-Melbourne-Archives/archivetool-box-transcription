package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.geometry.Point as GeoPoint
import au.edu.unimelb.archivetool.util.geometry.Point.Companion.flatten
import au.edu.unimelb.archivetool.util.geometry.Quad
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import kotlin.math.roundToInt
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacv.JavaCV.perspectiveTransform
import org.bytedeco.opencv.global.opencv_core.ALGO_HINT_DEFAULT
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_CN_SHIFT
import org.bytedeco.opencv.global.opencv_core.CV_MAT_DEPTH_MASK
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.CvMat
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point as CVPoint
import org.bytedeco.opencv.opencv_core.Scalar
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
            warpPerspective(
                sourceImage,
                result,
                it.mat,
                it.dSize,
            )
        }

        return result
    }

    fun perspectiveWarp(sourceImage: Mat, m: PerspectiveTransformMatrix): Mat {
        val result = Mat()
        warpPerspective(
            sourceImage,
            result,
            m.mat,
            m.dSize,
        )

        return result
    }

    fun perspectiveTransformRel(point: GeoPoint, m: PerspectiveTransformMatrix): GeoPoint {
        val result = DoubleArray(2)

        val src = listOf(point.x, point.y).map(Int::toDouble).toDoubleArray()

        perspectiveTransform(src, result, CvMat(m.mat))

        return result.map { it.roundToInt() }.take(2).let { (x, y) -> GeoPoint.create(x, y) }
    }

    fun readImage(path: Path): Mat = imread(path.pathString)

    fun Mat.writeImage(path: Path) {
        path.createParentDirectories()
        imwrite(path.pathString, this)
    }
}
