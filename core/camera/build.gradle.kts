// How the eye perceives: CameraX + on-device ML Kit face detection, and scripted fake sources.
plugins {
    alias(libs.plugins.myeye.android.library)
}

android {
    namespace = "com.salg.myeye.camera"
}

dependencies {
    // Part of this module's public API: FaceSource emits Flow<Perception> and takes a StateFlow.
    api(project(":core:watch"))
    api(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mlkit.face.detection)

    testImplementation(libs.kotlinx.coroutines.test)
}
