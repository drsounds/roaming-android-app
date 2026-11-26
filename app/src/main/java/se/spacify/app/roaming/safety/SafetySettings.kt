package se.spacify.app.roaming.safety

import android.content.Context
import android.content.SharedPreferences

/** How often the activity bubble reminds the user to keep track of their surroundings. */
enum class ReminderInterval(val minutes: Int) {
    TWO(2),
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30);

    val millis: Long get() = minutes * 60_000L

    companion object {
        val DEFAULT = FIVE

        /** The interval for [minutes], or [DEFAULT] for a value that isn't offered (there is no "off"). */
        fun fromMinutes(minutes: Int): ReminderInterval = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}

/**
 * The user's safety settings, stored in app-private SharedPreferences: which version of the safety
 * disclaimer they accepted, and how often the bubble shows the surroundings reminder.
 */
object SafetySettings {
    /** Bump when the disclaimer text changes in a way users must accept again. */
    const val DISCLAIMER_VERSION = 1

    /** How long the reminder stays on the bubble before it goes back to the current motion. */
    const val REMINDER_DISPLAY_MILLIS = 6_000L

    private const val PREFS = "safety"
    private const val KEY_ACCEPTED_VERSION = "disclaimer_accepted_version"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDisclaimerAccepted(context: Context): Boolean =
        prefs(context).getInt(KEY_ACCEPTED_VERSION, 0) >= DISCLAIMER_VERSION

    fun acceptDisclaimer(context: Context) =
        prefs(context).edit().putInt(KEY_ACCEPTED_VERSION, DISCLAIMER_VERSION).apply()

    fun reminderInterval(context: Context): ReminderInterval =
        ReminderInterval.fromMinutes(prefs(context).getInt(KEY_REMINDER_MINUTES, ReminderInterval.DEFAULT.minutes))

    fun setReminderInterval(context: Context, interval: ReminderInterval) =
        prefs(context).edit().putInt(KEY_REMINDER_MINUTES, interval.minutes).apply()
}
