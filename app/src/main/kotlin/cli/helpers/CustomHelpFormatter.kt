package au.edu.unimelb.archivetool.app.cli.helpers

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.mordant.rendering.Widget
import kotlin.collections.get
import kotlin.text.get

class CustomHelpFormatter(
    context: Context,
) : MordantHelpFormatter(context, showDefaultValues = true, showRequiredTag = true) {
    override fun collectParameterSections(
        parameters: List<ParameterHelp>,
    ): List<RenderedSection<Widget>> = buildList {
        addAll(renderArguments(parameters))
        addAll(renderOptions(parameters))
        addAll(renderCommands(parameters))
    }

    override fun renderOptions(parameters: List<ParameterHelp>): List<RenderedSection<Widget>> {
        val groupsByName =
            parameters.filterIsInstance<ParameterHelp.Group>().associateBy { it.name }
        return parameters.filterIsInstance<ParameterHelp.Option>().groupBy { it.groupName }.toList()
            .sortedBy { it.first != null } // Put the ungrouped options first
            .filter { it.second.isNotEmpty() }.map { (title, params) ->
                val renderedTitle = renderSectionTitle(title ?: localization.optionsTitle())
                val content = renderOptionGroup(groupsByName[title]?.help, params)
                RenderedSection(styleSectionTitle(renderedTitle), content)
            }.toList()
    }
}