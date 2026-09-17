package au.edu.unimelb.archivetool.switchboard.interactions.export

import kotlin.collections.mapNotNull
import kotlin.reflect.full.companionObject

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
sealed class RecordSetID {
    abstract val id: String

    // Used to generate the canonical name for the CMS.
    // UoM naming convention is:
    //  "<Record Set ID> Unit <%04d Box Number>" -- ie. "1974.0029 Unit 0319".
    fun CSVString(runningNumber: Int) = "$id Unit ${"%04d".format(runningNumber)}"

    companion object {
        inline fun <reified I : RecordSetID> List<RecordSetID>.findSubclass(): I? =
            this.firstOrNull { it is I }?.let { it as I }

        private fun searchInOrder(list: List<RecordSetID>): RecordSetID? =
            list.findSubclass<ModernID>()
                ?: list.findSubclass<OldMultiID>()
                ?: list.findSubclass<OldID>()
                ?: list.findSubclass<OldShorthandID>()

        fun scanForAll(transcription: String): RecordSetID? {
            val ids =
                RecordSetID::class.sealedSubclasses.mapNotNull {
                    (it.companionObject as RecordSetIDCompanion).scanFor(transcription)
                }
            return searchInOrder(ids)
        }
    }

    protected interface RecordSetIDCompanion {
        val pattern: Regex

        fun scanFor(transcription: String): RecordSetID?
    }

    class ModernID private constructor(override val id: String) : RecordSetID() {
        companion object : RecordSetIDCompanion {
            override val pattern = "UMA-(AC|SR)E?-\\d{8,9}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { ModernID(it.value) }
            }
        }
    }

    class OldMultiID private constructor(override val id: String) : RecordSetID() {
        companion object : RecordSetIDCompanion {
            override val pattern = "(?:.*${OldID.pattern}.*){2,}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldMultiID(it.value) }
            }
        }
    }

    class OldID private constructor(override val id: String) : RecordSetID() {
        companion object : RecordSetIDCompanion {
            override val pattern = "\\d{4}\\.\\d{4}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldID(it.value) }
            }
        }
    }

    class OldShorthandID private constructor(override val id: String) : RecordSetID() {
        companion object : RecordSetIDCompanion {
            override val pattern = "\\d{2}/\\d{1,4}".toRegex()

            override fun scanFor(transcription: String): RecordSetID? {
                val ids = pattern.findAll(transcription)
                return ids.firstOrNull()?.let { OldShorthandID(it.value) }
            }
        }
    }
}
