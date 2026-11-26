package se.spacify.app.roaming.safety

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIntervalTest {
    @Test
    fun fromMinutesFindsOfferedIntervals() {
        for (interval in ReminderInterval.entries) {
            assertEquals(interval, ReminderInterval.fromMinutes(interval.minutes))
        }
    }

    @Test
    fun unknownOrZeroFallsBackToDefaultSoRemindersCannotBeTurnedOff() {
        assertEquals(ReminderInterval.DEFAULT, ReminderInterval.fromMinutes(0))
        assertEquals(ReminderInterval.DEFAULT, ReminderInterval.fromMinutes(-1))
        assertEquals(ReminderInterval.DEFAULT, ReminderInterval.fromMinutes(7))
    }

    @Test
    fun millis() {
        assertEquals(300_000L, ReminderInterval.FIVE.millis)
    }
}
