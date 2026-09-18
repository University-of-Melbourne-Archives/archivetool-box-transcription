package au.edu.unimelb.archivetool.gui.segmentation

import au.edu.unimelb.archivetool.switchboard.jobstate.JobFile
import io.nacular.doodle.core.View
import io.nacular.doodle.image.Image
import io.nacular.doodle.image.ImageLoader
import java.nio.file.Path

class ImageViewport(private val jobs: Map<Path, JobFile?>, private val imageLoader: ImageLoader) : View() {
    private var image: Image? = null

}