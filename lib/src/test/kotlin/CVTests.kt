import au.edu.unimelb.archivetool.util.wrap.CV
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bytedeco.opencv.global.opencv_core.CV_16S
import org.bytedeco.opencv.global.opencv_core.CV_16U
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.CV_32S
import org.bytedeco.opencv.global.opencv_core.CV_64F
import org.bytedeco.opencv.global.opencv_core.CV_8S
import org.bytedeco.opencv.global.opencv_core.CV_8U

class CVTests {
    @Test
    fun colorDepthsMatchOrdinal() {
        val expected: Map<CV.ColourSpace.DepthType, Int> =
            mapOf(
                CV.ColourSpace.DepthType.U8 to CV_8U,
                CV.ColourSpace.DepthType.I8 to CV_8S,
                CV.ColourSpace.DepthType.U16 to CV_16U,
                CV.ColourSpace.DepthType.I16 to CV_16S,
                CV.ColourSpace.DepthType.I32 to CV_32S,
                CV.ColourSpace.DepthType.F32 to CV_32F,
                CV.ColourSpace.DepthType.F64 to CV_64F,
            )
        CV.ColourSpace.DepthType.entries.forEach { assertEquals(expected[it], it.ordinal) }
    }
}
