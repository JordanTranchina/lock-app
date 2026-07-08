package com.nathanb.lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.data.model.Schedule
import java.util.Calendar
import java.util.TimeZone

/**
 * Registers exact alarms that fire [ScheduleReceiver] to auto-engage and auto-release the lock
 * at the times configured in each [Schedule].
 *
 * Alarms are one-shot: the receiver re-schedules the next occurrence after each fire (and boot,
 * time changes, and schedule edits all trigger [rescheduleAll]). Two alarms exist per schedule —
 * a "start" (lock) and an "end" (unlock) — distinguished by a stable per-schedule request code.
 */
object ScheduleManager {

    private const val TAG = "ScheduleManager"

    const val ACTION_ALARM = "com.nathanb.lock.SCHEDULE_ALARM"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_EVENT = "event"
    const val EVENT_START = "start"
    const val EVENT_END = "end"

    /** Cancel then re-register alarms for the given schedules. Disabled schedules are only cancelled. */
    fun rescheduleAll(context: Context, schedules: List<Schedule>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        for (schedule in schedules) {
            cancel(context, am, schedule.id, EVENT_START)
            cancel(context, am, schedule.id, EVENT_END)
            if (!schedule.enabled) continue

            nextTriggerAtMillis(schedule.startMinuteOfDay, schedule.daysMask, now, zone)?.let {
                setAlarm(context, am, schedule.id, EVENT_START, it)
            }
            nextTriggerAtMillis(schedule.endMinuteOfDay, schedule.daysMask, now, zone)?.let {
                setAlarm(context, am, schedule.id, EVENT_END, it)
            }
        }
    }

    /** Cancel both alarms for a single schedule (used before deletion). */
    fun cancelSchedule(context: Context, scheduleId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        cancel(context, am, scheduleId, EVENT_START)
        cancel(context, am, scheduleId, EVENT_END)
    }

    private fun setAlarm(
        context: Context,
        am: AlarmManager,
        scheduleId: Long,
        event: String,
        triggerAtMillis: Long,
    ) {
        val pi = pendingIntent(context, scheduleId, event, PendingIntent.FLAG_UPDATE_CURRENT)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Exact-alarm permission not granted: fall back to a best-effort Doze-friendly alarm.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            // Defensive: some OEMs revoke exact-alarm at runtime. Never crash the caller.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Set $event alarm for schedule $scheduleId at $triggerAtMillis")
    }

    private fun cancel(context: Context, am: AlarmManager, scheduleId: Long, event: String) {
        val pi = pendingIntent(context, scheduleId, event, PendingIntent.FLAG_NO_CREATE)
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun pendingIntent(
        context: Context,
        scheduleId: Long,
        event: String,
        extraFlags: Int,
    ): PendingIntent? {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_EVENT, event)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(scheduleId, event),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Stable, collision-free request code: two slots per schedule (start = even, end = odd). */
    private fun requestCode(scheduleId: Long, event: String): Int {
        val base = (scheduleId.toInt() and 0x3FFFFFFF) shl 1
        return if (event == EVENT_END) base or 1 else base
    }

    /**
     * Next wall-clock time (epoch millis) at or after [now] that matches [minuteOfDay] on a day
     * enabled in [daysMask]. Returns null if no day is selected.
     *
     * Pure and time-zone explicit for testability. [daysMask]: bit 0 = Monday … bit 6 = Sunday.
     */
    fun nextTriggerAtMillis(
        minuteOfDay: Int,
        daysMask: Int,
        now: Long,
        zone: TimeZone,
    ): Long? {
        if (daysMask and Schedule.ALL_DAYS == 0) return null
        val base = Calendar.getInstance(zone).apply { timeInMillis = now }
        // Look ahead up to 8 days so a time already passed today rolls to the next enabled day
        // (7 alone can miss the same weekday next week when only today is selected).
        for (offset in 0..8) {
            val c = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                set(Calendar.MINUTE, minuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= now) continue
            val dow = c.get(Calendar.DAY_OF_WEEK)
            val bit = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
            if ((daysMask shr bit) and 1 == 1) return c.timeInMillis
        }
        return null
    }
}
