package se.spacify.app.roaming.motion

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs ML Kit's bundled pose model on front-camera frames. Frames are analysed in memory and
 * dropped immediately: nothing is recorded, stored or sent anywhere.
 */
class CameraPoseSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val emitter: MotionEmitter,
) {
    private val classifier = PoseClassifier(frontCamera = true)
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, ::analyze)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the front camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        classifier.reset()
        detector.close()
        executor.shutdown()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener(executor) { pose -> handle(pose) }
            .addOnCompleteListener(executor) { proxy.close() }
    }

    private fun handle(pose: Pose) {
        val now = SystemClock.elapsedRealtime()
        val body = pose.toBodyPose()
        if (body == null) {
            emitter.submit(MotionState.IDLE, now)
            return
        }
        val (state, stepped) = classifier.classify(body, now)
        emitter.submit(state, now, pulse = stepped)
    }

    private fun Pose.toBodyPose(): BodyPose? {
        fun p(type: Int): Point? = getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= MIN_LIKELIHOOD }
            ?.position
            ?.let { Point(it.x, it.y) }
        return BodyPose(
            nose = p(PoseLandmark.NOSE) ?: return null,
            leftShoulder = p(PoseLandmark.LEFT_SHOULDER) ?: return null,
            rightShoulder = p(PoseLandmark.RIGHT_SHOULDER) ?: return null,
            leftHip = p(PoseLandmark.LEFT_HIP) ?: return null,
            rightHip = p(PoseLandmark.RIGHT_HIP) ?: return null,
            leftWrist = p(PoseLandmark.LEFT_WRIST) ?: return null,
            rightWrist = p(PoseLandmark.RIGHT_WRIST) ?: return null,
            leftKnee = p(PoseLandmark.LEFT_KNEE),
            rightKnee = p(PoseLandmark.RIGHT_KNEE),
        )
    }

    private companion object {
        const val TAG = "CameraPoseSource"
        const val MIN_LIKELIHOOD = 0.5f
    }
}
