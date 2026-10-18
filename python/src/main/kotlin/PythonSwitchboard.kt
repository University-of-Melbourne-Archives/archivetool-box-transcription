package au.edu.unimelb.archivetool.python

import au.edu.unimelb.archivetool.python.interactions.PythonOCRInteraction
import au.edu.unimelb.archivetool.switchboard.Switchboard

val pythonSwitchboard by lazy {
    Switchboard.with(PythonOCRInteraction())
}