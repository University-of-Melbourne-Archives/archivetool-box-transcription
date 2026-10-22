package buildsrc.convention.kotlin.jvm

import buildsrc.convention.util.Module
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType

object Tests: Module {
    override fun configure(project: Project) = project.tasks.withType<Test>().configureEach {
        // Configure all test Gradle tasks to use JUnitPlatform.
        useJUnitPlatform()

        // Log information about all test results, not only the failed ones.
        testLogging {
            events(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED
            )
        }
    }
}
