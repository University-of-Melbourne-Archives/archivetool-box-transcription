@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class SemiManual(val extraSteps: String, val lastSucceeded: String = "never")
