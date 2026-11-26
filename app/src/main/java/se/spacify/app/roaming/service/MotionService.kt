package se.spacify.app.roaming.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import se.spacify.app.roaming.R
import se.spacify.app.roaming.motion.CameraPoseSource
import se.spacify.app.roaming.motion.MotionBroadcaster
import se.spacify.app.roaming.motion.MotionBus
import se.spacify.app.roaming.motion.MotionEmitter
import se.spacify.app.roaming.motion.MotionEvent
import se.spacify.app.roaming.motion.StepSensorSource
import se.spacify.app.roaming.overlay.OverlayBubble
import se.spacify.app.roaming.ui.MainActivity

/**
 * Foreground service that owns all motion tracking. Broadcasts are only sent while this service
 * is running, and it always shows an ongoing notification (with a Stop action), so the user can
 * always see that Roaming is tracking and turn it off.
 */
class MotionService : LifecycleService() {

    private var stepSource: StepSensorSource? = null
    private var cameraSource: CameraPoseSource? = null
    private var overlay: OverlayBubble? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (stepSource != null || cameraSource != null) return START_NOT_STICKY

        val useSteps = intent?.getBooleanExtra(EXTRA_USE_STEPS, true) ?: true
        val useCamera = intent?.getBooleanExtra(EXTRA_USE_CAMERA, false) ?: false
        try {
            startInForeground(useSteps, useCamera)
        } catch (e: Exception) {
            // Missing runtime permission or a background start the OS refused: never track silently.
            Log.e(TAG, "Could not start foreground tracking", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val broadcaster = MotionBroadcaster(this)
        if (useSteps) {
            stepSource = StepSensorSource(this, MotionEmitter(MotionEvent.Source.STEP_SENSOR, sink = broadcaster::send))
                .also { it.start() }
        }
        if (useCamera) {
            cameraSource = CameraPoseSource(this, this, MotionEmitter(MotionEvent.Source.CAMERA, sink = broadcaster::send))
                .also { it.start() }
        }
        MotionBus.setRunning(true)

        if (Settings.canDrawOverlays(this)) {
            overlay = OverlayBubble(this).also { bubble ->
                bubble.show()
                lifecycleScope.launch {
                    MotionBus.latest.filterNotNull().collect { bubble.update(it.new) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stepSource?.stop()
        cameraSource?.stop()
        overlay?.hide()
        stepSource = null
        cameraSource = null
        overlay = null
        MotionBus.setRunning(false)
        super.onDestroy()
    }

    private fun startInForeground(useSteps: Boolean, useCamera: Boolean) {
        createChannel()
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (useSteps) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            if (useCamera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && useCamera) {
            types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(useSteps, useCamera), types)
    }

    private fun buildNotification(useSteps: Boolean, useCamera: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MotionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val inputs = listOfNotNull(
            getString(R.string.input_steps).takeIf { useSteps },
            getString(R.string.input_camera).takeIf { useCamera },
        ).joinToString(" + ")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_roaming)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, inputs))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val TAG = "MotionService"
        private const val CHANNEL_ID = "roaming_tracking"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "se.spacify.app.roaming.action.STOP"
        const val EXTRA_USE_STEPS = "use_steps"
        const val EXTRA_USE_CAMERA = "use_camera"

        fun start(context: Context, useSteps: Boolean, useCamera: Boolean) {
            val intent = Intent(context, MotionService::class.java)
                .putExtra(EXTRA_USE_STEPS, useSteps)
                .putExtra(EXTRA_USE_CAMERA, useCamera)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MotionService::class.java).setAction(ACTION_STOP))
        }
    }
}
