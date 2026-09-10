package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.mordant.rendering.Widget

// Customises the layout produced by --help.
class CustomHelpFormatter(context: Context) :
    MordantHelpFormatter(
        context,

        // Show option default values in the help output.
        showDefaultValues = true,

        // Mark required arguments and options.
        showRequiredTag = true,
    ) {
    // Define the order of help sections:
    // Arguments → Options → Commands
    override fun collectParameterSections(
        parameters: List<ParameterHelp>
    ): List<RenderedSection<Widget>> = buildList {
        addAll(renderArguments(parameters))
        addAll(renderOptions(parameters))
        addAll(renderCommands(parameters))
    }

    // Organise options into named groups.
    override fun renderOptions(parameters: List<ParameterHelp>): List<RenderedSection<Widget>> {

        // Find the available option-group descriptions.
        val groupsByName =
            parameters.filterIsInstance<ParameterHelp.Group>().associateBy { it.name }

        return parameters

            // Select options from all help parameters.
            .filterIsInstance<ParameterHelp.Option>()

            // Group options by their group name.
            .groupBy { it.groupName }
            .toList()

            // Show ordinary options before named option groups.
            .sortedBy { it.first != null }

            // Ignore empty groups.
            .filter { it.second.isNotEmpty() }

            // Render each option group as a help section.
            .map { (title, params) ->
                val renderedTitle = renderSectionTitle(title ?: localization.optionsTitle())

                val content = renderOptionGroup(groupsByName[title]?.help, params)

                RenderedSection(styleSectionTitle(renderedTitle), content)
            }
            .toList()
    }
}
