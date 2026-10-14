package au.edu.unimelb.archivetool.switchboard.interactions.imagemanip

import au.edu.unimelb.archivetool.switchboard.jobstate.Boundary
import au.edu.unimelb.archivetool.util.Colour
import au.edu.unimelb.archivetool.util.math.geometry.Quad
import au.edu.unimelb.archivetool.util.wrap.CV
import org.bytedeco.opencv.opencv_core.Mat

// NOT an Interaction, should be called from crop or flatten as need be.
object LabelBlanker {
    // Transform should transform the points from input image space to output space.
    // Negative or out-of-bounds coordinates will fail, so make sure to confine them.
    fun blankLabels(img: Mat, labels: List<Boundary>, transform: Quad.() -> Quad): Mat {
        val output = Mat()
        img.copyTo(output)

        labels.forEach {
            val newCoordinates = it.points.transform()

            CV.fillQuad(output, newCoordinates, Colour.WHITE)
        }
        return output
    }
}
