package au.edu.unimelb.archivetool.util

import au.edu.unimelb.archivetool.util.geometry.Quad
import au.edu.unimelb.archivetool.util.geometry.Rectangle
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.opencv.global.opencv_core.ALGO_HINT_DEFAULT
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size

object CV {
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

    // Inspired by https://github.com/bytedeco/javacv/blob/master/samples/PerspectiveWarpDemo.java
    fun perspectiveWarp(sourceImage: Mat, bounds: Quad, destinationBounds: Rectangle): Mat {
        val bounds = bounds.toClockwise()
        val destinationBounds = destinationBounds.toClockwise()

        val srcCorners = FloatPointer(*bounds.floatArray)

        val dstCorners = FloatPointer(*destinationBounds.floatArray)

        val src = Mat(Size(2, 4), CV_32F, srcCorners)
        val dst = Mat(Size(2, 4), CV_32F, dstCorners)

        val perspectiveTransform: Mat = getPerspectiveTransform(src, dst)

        val result = Mat()
        warpPerspective(
            sourceImage,
            result,
            perspectiveTransform,
            Size(destinationBounds.width, destinationBounds.height),
        )

        src.release()
        dst.release()

        return result
    }

    fun readImage(path: Path): Mat = imread(path.pathString)

    fun Mat.writeImage(path: Path) {
        path.createParentDirectories()
        imwrite(path.pathString, this)
    }
}
