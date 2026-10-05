import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * `myeye.jvm.library`: a pure Kotlin module with no Android at all. Android imports don't even
 * compile here, which is the point.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(JAVA_VERSION.toString()))
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
