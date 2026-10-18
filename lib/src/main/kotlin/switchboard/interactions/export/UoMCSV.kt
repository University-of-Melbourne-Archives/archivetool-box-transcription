package au.edu.unimelb.archivetool.switchboard.interactions.export

import au.edu.unimelb.archivetool.switchboard.interactions.export.CSV.Companion.Field
import au.edu.unimelb.archivetool.util.extension.with4IgnoredParameters

object UoMCSV: CSV {
    // The fields required for import into our Collection Management System.
    // Node ID     - Internal database key required to import the entry; cannot be inferred from
    // images of a box.
    //               It might be possible to have some additional input that maps "Node Title" to
    // this field.
    //               Would be a nice to have, but too complex for our timeframe, given the number of
    // label variants.
    // Node Type   - Always "Unit"
    // Node Title  - The canonical name of the box within the CMS.
    //                  This is handled by RecordSetID.
    // Description – The handwritten annotations on the box. '\n' should be replaced by ';'. This is
    // done internally.
    //                  The CSV library automatically quotes strings with commas.
    // Date Range  – This is a nice to have. The details of this are handled in util/DateRange.
    val CMSFields =
        Field.builder(
            "Node ID" to { "?????" }.with4IgnoredParameters(),
            "Node Type" to { "Item" }.with4IgnoredParameters(),
            "Node Title" to
                    { _, _, _, box ->
                        box.metadata?.id?.CSVString(box.metadata.unitNumber ?: 0)
                    },
            "Description" to
                    { _, _, _, box ->
                        // TODO: Also transcribe any non-primary labels.
                        box.metadata?.description ?: ""
                    },
            "Date Range" to { _, _, _, box -> box.metadata?.dateRange?.CSVString ?: "" },
        )
    // Additional Fields, anything not required for the CMS, but useful for manual review.
    val additionalFields = Field.builder()

    override val fields = CMSFields + additionalFields
}