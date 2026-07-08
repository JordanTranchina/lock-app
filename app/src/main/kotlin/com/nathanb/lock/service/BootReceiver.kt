package com.nathanb.lock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Boot restores a running session; time/timezone changes invalidate pending alarm times.
        // All three cases require re-registering the schedule alarms.
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        if (!relevant) return

        if (BuildConfig.DEBUG) Log.d(TAG, "Received ${intent.action}, restoring lock state + schedules")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as LockApplication
                val state = app.repository.getLockState()

                if (intent.action == Intent.ACTION_BOOT_COMPLETED && state.isLocked) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Was locked before reboot, restarting foreground service")
                    LockForegroundService.start(appContext)
                }

                // Re-arm scheduled auto-lock alarms (alarms are cleared on reboot / time change).
                ScheduleManager.rescheduleAll(appContext, app.repository.getSchedules())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
