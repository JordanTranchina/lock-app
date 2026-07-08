package com.nathanb.lock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.EndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled auto-lock window starts or ends. Engages or releases the lock, then
 * re-registers the next occurrence of every schedule.
 *
 * Exact alarms grant a temporary background exemption, so starting the foreground service here
 * is allowed even when the app is not in the foreground.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleManager.ACTION_ALARM) return

        val scheduleId = intent.getLongExtra(ScheduleManager.EXTRA_SCHEDULE_ID, -1L)
        val event = intent.getStringExtra(ScheduleManager.EXTRA_EVENT) ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "Alarm fired: schedule=$scheduleId event=$event")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val app = appContext as LockApplication
        val repository = app.repository

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedule = repository.getSchedule(scheduleId)
                if (schedule != null && schedule.enabled) {
                    when (event) {
                        ScheduleManager.EVENT_START -> engageLock(appContext, repository, schedule.profileId)
                        ScheduleManager.EVENT_END -> releaseLock(appContext, repository)
                    }
                }
                // Re-arm the next occurrence of all schedules (this alarm was one-shot).
                ScheduleManager.rescheduleAll(appContext, repository.getSchedules())
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to handle schedule alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun engageLock(
        context: Context,
        repository: com.nathanb.lock.data.repository.LockRepository,
        profileId: Long?,
    ) {
        val state = repository.getLockState()
        // Respect any session already in progress (NFC / manual / no-escape) — don't override it.
        if (state.isLocked) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Already locked, skipping scheduled start")
            return
        }
        // Fall back to the default profile when the schedule's profile is unset or was deleted.
        val resolvedId = profileId?.let { repository.getProfile(it)?.id }
            ?: repository.getDefaultProfile()?.id
            ?: return
        repository.startLockSession(resolvedId, scheduled = true)
        LockForegroundService.start(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Scheduled lock engaged with profile $resolvedId")
    }

    private suspend fun releaseLock(
        context: Context,
        repository: com.nathanb.lock.data.repository.LockRepository,
    ) {
        val state = repository.getLockState()
        // Only release sessions that a schedule started — never cut short an NFC/manual/no-escape one.
        if (!state.isLocked || !state.isScheduled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "No scheduled session to release")
            return
        }
        repository.endLockSession(EndReason.SCHEDULE.value)
        LockForegroundService.stop(context)
        if (BuildConfig.DEBUG) Log.d(TAG, "Scheduled lock released")
    }
}
