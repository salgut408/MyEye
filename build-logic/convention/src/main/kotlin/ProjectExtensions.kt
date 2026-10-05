import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `gradle/libs.versions.toml` catalog, usable from plugin code. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(name: String): Int = findVersion(name).get().requiredVersion.toInt()

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** Java level for app code. AGP's built-in Kotlin follows `compileOptions`, so this sets Kotlin's target too. */
internal val JAVA_VERSION = JavaVersion.VERSION_11

/** Settings every Android module (app or library) shares. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk {
        version = release(libs.intVersion("compileSdk"))
    }
    android.defaultConfig.minSdk = libs.intVersion("minSdk")
    android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    android.compileOptions.sourceCompatibility = JAVA_VERSION
    android.compileOptions.targetCompatibility = JAVA_VERSION
}
