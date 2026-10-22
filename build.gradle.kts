allprojects {
    configurations.all {
        resolutionStrategy {
            // Force newer version of the kotlinx-datetime libs
            // because we use features not in Doodle's strictly required version
            force(
                libs.kotlinxDatetime, libs.kotlinxCoroutines, libs.kotlinxCoroutinesSwing
            )
        }
    }
}