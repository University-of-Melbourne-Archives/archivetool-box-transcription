package au.edu.unimelb.archivetool.switchboard.jobstate

import au.edu.unimelb.archivetool.util.extension.counted
import kotlin.reflect.full.companionObjectInstance
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Record Set IDs are in one of several formats:
//  1.  <year>.<%04d running number> -- ie. 1976.0029
//          (Sometimes, particularly before any ambitions of digitisation,
//           this is written as just <2 digit year>/<running number> -- ie. 76/29)
//          Additionally, this is often handwritten on older boxes.
//  2.  In some cases where multiple sets of records were consolidated together,
//          rather than assigning a new number, they would write all the old numbers on the label.
//          An example is in our sample set, listing 1974.0029, 1974.0030, 1997.0138.
//          In these cases, choose the earliest one, with ties broken by the lowest box number.
//  3.  Newer record IDs conveniently match the RegEx /UMA-(AC|SR)E?-\d{8,9}/ -- ie.
//          UMA-AC-000000330 or UMA-SRE-20160038
// There _should_ only be one type of set ID per box,
//      and we should warn the user if there are multiple.
// Format 3 should be preferred if possible in this case.
@Serializable
sealed class RecordSetID {
    abstract val id: String

    abstract fun matchesType(other: RecordSetID): Boolean

    override fun equals(other: Any?): Boolean {
        return other is RecordSetID && other.id == id
    }

    // Used to generate the canonical name for the CMS.
    // UoM naming convention is:
    //  "<Record Set ID> Unit <%04d Box Number>" -- ie. "1974.0029 Unit 0319".
    fun CSVString(unitNumber: Int) = "$id Unit ${"%04d".format(unitNumber)}"

    companion object {
        inline fun <reified I : RecordSetID> List<RecordSetID>.findSubclass(): I? =
            this.firstOrNull { it is I }?.let { it as I }

        private fun searchInOrder(list: List<RecordSetID>): RecordSetID? =
            list.findSubclass<ModernID>()
                ?: list.findSubclass<OldMultiID>()
                ?: list.findSubclass<OldID>()
                ?: list.findSubclass<OldShorthandID>()

        // null be unreachable unless the list is empty.

        fun scanForAll(transcription: String): RecordSetID? {
            val ids =
                RecordSetID::class.sealedSubclasses.mapNotNull {
                    (it.companionObjectInstance as RecordSetIDCompanion).scanFor(transcription)
                }
            return searchInOrder(ids)
        }

        fun fromOCROutput(ids: List<String>): RecordSetID? {
            val scanned = ids.map { scanForAll(it) }
            val type = scanned.firstOrNull() ?: return null
            return when (type) {
                is OldID -> {
                    if (scanned.all { it is OldID } && scanned.size > 1) {
                        OldMultiID(scanned.joinToString { it!!.id })
                    } else {
                        type
                    }
                }
                else -> type
            }
        }

        // Sort by priority order, then the most frequent one remaining, with ties being broken by
        // alphabetical order or something?
        fun bestGuess(options: List<RecordSetID>): RecordSetID {
            val typeOf = searchInOrder(options) ?: return OldShorthandID("00/0")
            val ofBestType = options.filter { typeOf.matchesType(it) }
            // TODO: Better tie-breaking if counts match.
            return ofBestType.counted().maxBy { it.first }.second
        }

        // Validates a single string, where the entire string must be a valid ID
        // Throws IllegalArgumentException with a corresponding user message on fail
        fun fromUserInput(input: String): RecordSetID {
            val trimmedID = input.trim()

            val scannedID =
                scanForAll(trimmedID)
                    ?: throw IllegalArgumentException(
                        "\"$trimmedID\" is not a valid Node Title " +
                            "(expected e.g. 1976.0029, 76/29 or UMA-AC-000000330)."
                    )

            require(scannedID.id == trimmedID) {
                "\"$trimmedID\" contains text that is not part of a valid Node Title " +
                    "(found \"${scannedID.id}\")."
            }

            return scannedID
        }
    }

    protected interface RecordSetIDCompanion {
        val pattern: Regex

        fun scanFor(transcription: String): RecordSetID?
    }

    @Serializable
    @SerialName("modern")
    data class ModernID(override val id: String) : RecordSetID() {
        init {
            require("^${pattern.pattern}$".toRegex().matches(id))
        }

        companion object : RecordSetIDCompanion {
            override val pattern = "UMA-(AC|SR)E?-\\d{8,9}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { ModernID(it.value) }
            }
        }

        override fun matchesType(other: RecordSetID): Boolean = other is ModernID
    }

    @Serializable
    @SerialName("old-multi")
    data class OldMultiID(override val id: String) : RecordSetID() {
        init {
            require("^${pattern.pattern}$".toRegex().matches(id))
        }

        override fun matchesType(other: RecordSetID): Boolean = other is OldMultiID

        companion object : RecordSetIDCompanion {
            override val pattern = "(?:.*${OldID.pattern}.*){2,}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldMultiID(it.value) }
            }
        }
    }

    @Serializable
    @SerialName("old")
    data class OldID(override val id: String) : RecordSetID() {
        init {
            require("^${pattern.pattern}$".toRegex().matches(id))
        }

        override fun matchesType(other: RecordSetID): Boolean = other is OldID

        companion object : RecordSetIDCompanion {
            override val pattern = "\\d{4}\\.\\d{4}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldID(it.value) }
            }
        }
    }

    @Serializable
    @SerialName("shorthand")
    data class OldShorthandID(override val id: String) : RecordSetID() {
        init {
            require("^${pattern.pattern}$".toRegex().matches(id))
        }

        override fun matchesType(other: RecordSetID): Boolean = other is OldShorthandID

        companion object : RecordSetIDCompanion {
            override val pattern = "\\d{2}/\\d{1,4}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldShorthandID(it.value) }
            }
        }
    }
}
