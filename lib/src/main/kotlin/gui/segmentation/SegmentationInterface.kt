import io.nacular.doodle.application.Application
import io.nacular.doodle.core.Display
import io.nacular.doodle.core.view
import io.nacular.doodle.drawing.Color.Companion.Black
import io.nacular.doodle.drawing.text

class SegmentationReview(display: Display): Application {
    init {
        display += view {
            render = {
                text("Hello, world!", color = Black)
            }
        }
    }

    override fun shutdown() {}
}