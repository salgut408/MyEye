// Top-level build file. Plugins are declared here (not applied) so every module and the
// convention plugins in build-logic/ share one classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
