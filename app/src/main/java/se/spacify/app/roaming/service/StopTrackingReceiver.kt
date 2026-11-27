package se.spacify.app.roaming.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the notification's Stop action. It stops [MotionService] with `stopService`, which never
 * starts a new service instance and is never refused as a background start, unlike delivering a
 * stop intent to the service itself.
 */
class StopTrackingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MotionService.ACTION_STOP) {
            context.stopService(Intent(context, MotionService::class.java))
        }
    }
}
