// The look shared by every screen: always-dark MyEyeTheme on a near-black stage, and its colors.
plugins {
    alias(libs.plugins.myeye.android.library)
    alias(libs.plugins.myeye.android.compose)
}

android {
    namespace = "com.salg.myeye.core.ui"
}
