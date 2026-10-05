package com.salg.myeye.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.salg.myeye.watch.Perception
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera + on-device ML Kit face detection. The camera runs exactly as long as the flow is
 * collected: collection binds CameraX to a private lifecycle, cancellation unbinds it and releases
 * the detector. Frames never leave this class: only face positions and a brightness number do.
 * Nothing is saved, uploaded or logged.
 *
 * Must be collected on the main thread (CameraX binding and lifecycle requirements).
 */
class CameraFaceSource(private val context: Context) : FaceSource {

    override fun perceptions(): Flow<Perception> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            send(Perception.NoPermission)
            awaitClose()
            return@callbackFlow
        }

        val lifecycle = CollectionLifecycle()
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // eye-open probability
                .enableTracking()
                .build(),
        )
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { image -> analyze(image, detector) }

        var provider: ProcessCameraProvider? = null
        try {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context).also { provider = it }
            lifecycle.start()
            val camera = cameraProvider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            // Another app holding the camera, the camera being disabled, etc.: the eye is blind.
            camera.cameraInfo.cameraState.observe(lifecycle) { state ->
                state.error?.let { trySend(Perception.CameraError(IllegalStateException("Camera error ${it.code}", it.cause))) }
            }
        } catch (e: Exception) { // no front camera, binding failure, permission revoked mid-way
            if (e is CancellationException) throw e
            send(Perception.CameraError(e))
        }

        awaitClose {
            analysis.clearAnalyzer()
            provider?.unbind(analysis)
            lifecycle.destroy()
            detector.close()
            executor.shutdown()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun ProducerScope<Perception>.analyze(image: ImageProxy, detector: FaceDetector) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val luma = meanLuma(image.planes[0].buffer) // read before the frame is closed
        val timestampMs = image.imageInfo.timestamp / 1_000_000
        val width = image.width
        val height = image.height

        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener(DirectExecutor) { faces ->
                val seen = faces.mapNotNull { face ->
                    val id = face.trackingId ?: return@mapNotNull null
                    val box = face.boundingBox
                    mapFace(
                        id = id,
                        centerX = box.exactCenterX(),
                        centerY = box.exactCenterY(),
                        boxWidth = box.width().toFloat(),
                        imageWidth = width,
                        imageHeight = height,
                        rotationDegrees = rotation,
                        mirror = true, // front camera
                        eyesOpen = eyesOpen(face.leftEyeOpenProbability, face.rightEyeOpenProbability),
                    )
                }
                trySend(Perception.Frame(seen, luma, timestampMs))
            }
            // A failed detection just drops the frame; the next one comes ~60 ms later.
            .addOnCompleteListener(DirectExecutor) { image.close() } // always, success or failure
    }

    /**
     * Detector callbacks run on whatever thread finished the task, so they still run (and close the
     * frame) after the analysis executor has been shut down.
     */
    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    /** A lifecycle that is STARTED/RESUMED while the flow is collected and DESTROYED after. */
    private class CollectionLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
