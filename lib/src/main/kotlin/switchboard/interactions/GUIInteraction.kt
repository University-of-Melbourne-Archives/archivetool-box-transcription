package au.edu.unimelb.archivetool.switchboard.interactions

import au.edu.unimelb.archivetool.util.file.Image
import au.edu.unimelb.archivetool.util.wrap.Doodle

interface GUIInteraction {
    fun mayRequireConversion(): Boolean = true

    fun supported(): Image.FileType.Supported = Doodle.formatRequirements
}
