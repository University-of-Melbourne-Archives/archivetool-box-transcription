package au.edu.unimelb.archivetool.app.cli

import au.edu.unimelb.archivetool.app.cli.helpers.Command
import au.edu.unimelb.archivetool.switchboard.Switchboard
import au.edu.unimelb.archivetool.util.Git
import au.edu.unimelb.archivetool.util.os.Platform
import com.github.ajalt.clikt.core.Context
import kotlin.io.path.notExists

const val COPYRIGHT_SYM = "\u00a9"

class InfoCommand : Command(name = "info") {

    override fun help(context: Context): String =
        """
        Displays Legal and System Information about archivetool
        """
            .trimIndent()

    override fun run() {
        val configPath = Platform.configPath().resolve("config.json")

        val configMsg =
            if (configPath.notExists()) {
                Switchboard.config.writeDefault()
                "No configuration file was detected.\nWrote a default configuration file to $configPath."
            } else {
                "Using the configuration file at $configPath"
            }

        println(
            """
            archivetool -- Archive Digitisation Software (commit ${Git.HASH})
            Copyright $COPYRIGHT_SYM 2026 The University of Melbourne
    
            This program is free software: you can redistribute it and/or modify
            it under the terms of the GNU Affero General Public License as published by
            the Free Software Foundation, either version 3 of the License, or
            (at your option) any later version.
    
            This program is distributed in the hope that it will be useful,
            but WITHOUT ANY WARRANTY; without even the implied warranty of
            MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
            GNU Affero General Public License for more details.
    
            You should have received a copy of the GNU Affero General Public License
            along with this program. If not, see <https://www.gnu.org/licenses/>.
            
            $configMsg
            """
                .trimIndent()
        )
    }
}
