package se.spacify.app.roaming.motion

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Executors

/**
 * Runs ML Kit's bundled pose model on front-camera frames. Frames are analysed in memory and
 * dropped immediately: nothing is recorded, stored or sent anywhere.
 *
 * When the whole body (down to the hips) is in view, [PoseClassifier] detects jumps, squats,
 * leaning, raised arms and steps in place. Whenever it sees nothing, or only the head and shoulders
 * are in view (selfie mode, or a phone on an elliptical console), [UpperBodyTracker] reports
 * rhythmic upper-body exercise as walking / running. The gyroscope tells it how much the phone
 * itself is moving, so a hand-held camera isn't mistaken for the user moving.
 */
class CameraPoseSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val emitter: MotionEmitter,
) {
    private val classifier = PoseClassifier(frontCamera = true)
    private val upperBody = UpperBodyTracker()
    private val steadiness = CameraSteadiness()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            steadiness.onRotationRate(event.values[0], event.values[1], event.values[2], SystemClock.elapsedRealtime())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Runs work on [executor], and drops it once tracking has stopped. ML Kit finishes frames that
     * were in flight when [stop] ran; handing their callbacks to the shut-down executor threw a
     * [RejectedExecutionException] on ML Kit's thread and crashed the app.
     */
    private val safeExecutor = Executor { task ->
        if (stopped) return@Executor
        try {
            executor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Stopped meanwhile: nothing left to do with this frame.
        }
    }

    @Volatile
    private var stopped = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null

    fun start() {
        // Without a gyroscope we assume the phone is mounted (steadiness stays 1).
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Tracking may already have stopped (the service is gone): don't open the camera.
            if (stopped) return@addListener
            val provider = runCatching { future.get() }.getOrElse { e ->
                Log.e(TAG, "Could not get the camera", e)
                return@addListener
            }
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
            analysis.setAnalyzer(safeExecutor, ::analyze)
            this.analysis = analysis
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the front camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (stopped) return
        stopped = true
        sensorManager.unregisterListener(gyroListener)
        analysis?.clearAnalyzer()
        analysis = null
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        executor.shutdown()
        runCatching { detector.close() }
        classifier.reset()
        upperBody.reset()
        steadiness.reset()
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || stopped) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        try {
            detector.process(image)
                .addOnSuccessListener(safeExecutor) { pose -> handle(pose) }
                // Close the frame on whatever thread finishes it, so it is closed even after stop.
                .addOnCompleteListener { proxy.close() }
        } catch (e: Exception) {
            // The detector was closed under us.
            proxy.close()
        }
    }

    private fun handle(pose: Pose) {
        if (stopped) return
        val now = SystemClock.elapsedRealtime()
        // The upper body runs on every frame where head and shoulders are visible, so its rhythm
        // and intensity stay continuous when the hips move in and out of frame.
        val upper = pose.toUpperBodyPose()?.let { upperBody.update(it, now, steadiness.value) }
        val full = pose.toBodyPose()?.let { classifier.classify(it, now) }
        val (state, stepped) = when {
            // A whole-body motion (jump, squat, lean, knee lifts...) wins when there is one.
            full != null && full.first.motionType != MotionType.IDLE -> full
            upper != null -> upper
            else -> full ?: (MotionState.IDLE to false)
        }
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

    private fun Pose.toUpperBodyPose(): UpperBodyPose? {
        fun p(type: Int): Point? = getPoseLandmark(type)
            ?.takeIf { it.inFrameLikelihood >= MIN_LIKELIHOOD }
            ?.position
            ?.let { Point(it.x, it.y) }
        return UpperBodyPose(
            nose = p(PoseLandmark.NOSE) ?: return null,
            leftShoulder = p(PoseLandmark.LEFT_SHOULDER) ?: return null,
            rightShoulder = p(PoseLandmark.RIGHT_SHOULDER) ?: return null,
            leftElbow = p(PoseLandmark.LEFT_ELBOW),
            rightElbow = p(PoseLandmark.RIGHT_ELBOW),
            leftWrist = p(PoseLandmark.LEFT_WRIST),
            rightWrist = p(PoseLandmark.RIGHT_WRIST),
        )
    }

    private companion object {
        const val TAG = "CameraPoseSource"
        const val MIN_LIKELIHOOD = 0.5f
    }
}
