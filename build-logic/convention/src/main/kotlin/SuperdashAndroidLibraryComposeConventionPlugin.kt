import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Adds Compose to a module that already applies `superdash.android.library`.
 *
 * Enables `buildFeatures.compose` and applies the Kotlin Compose compiler
 * plugin. Module-specific Compose dependencies (BOM, UI libraries) stay
 * in the consuming module.
 */
class SuperdashAndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("superdash.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
