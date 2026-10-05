// The eye itself: pure geometry, a swappable Canvas renderer, and the animated LivingEye.
// Knows nothing about cameras or the state machine; it just draws an EyeBehavior.
plugins {
    alias(libs.plugins.myeye.android.library)
    alias(libs.plugins.myeye.android.compose)
}

android {
    namespace = "com.salg.myeye.eye"
}

dependencies {
    implementation(project(":core:ui"))
}
