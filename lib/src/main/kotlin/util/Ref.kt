package au.edu.unimelb.archivetool.util

import kotlin.reflect.KMutableProperty0

class Ref<T>(value: KMutableProperty0<T>) {
    var inner by value
}
