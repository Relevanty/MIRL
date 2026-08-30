package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SleepAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SleepAutomationScheduler.ACTION_ARM) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = SleepAutomationCoordinator(context).armCurrentWindow()
                Log.i(TAG, "Night automation result=$result")
                val scheduler = SleepAutomationScheduler(context)
                if (result == SleepAutomationCoordinator.ArmResult.ACTIVE_FOCUS ||
                    result == SleepAutomationCoordinator.ArmResult.ALARM_PERMISSION_MISSING
                ) scheduler.scheduleRetry() else scheduler.scheduleNext()
            } catch (error: Throwable) {
                Log.e(TAG, "Night automation failed", error)
                runCatching { SleepAutomationScheduler(context).scheduleNext() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SleepAutomation"
    }
}
