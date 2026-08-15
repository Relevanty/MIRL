package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.sleepalarm.data.db.AppDatabase
import com.personal.sleepalarm.data.repository.ReminderRepository
import com.personal.sleepalarm.service.ReminderNotificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_PRE = ReminderScheduler.ACTION_PRE
        const val ACTION_FIRE = ReminderScheduler.ACTION_FIRE
        const val ACTION_DONE = ReminderScheduler.ACTION_DONE
        const val ACTION_SNOOZE = ReminderScheduler.ACTION_SNOOZE
        const val ACTION_SNOOZE_FIRE = ReminderScheduler.ACTION_SNOOZE_FIRE
        const val EXTRA_REMINDER_ID = ReminderScheduler.EXTRA_REMINDER_ID
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        Log.d(TAG, "onReceive: action=$action id=$reminderId")

        if (reminderId < 0) {
            Log.w(TAG, "bad reminderId, exit")
            return
        }

        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)
        val repository = ReminderRepository(database.reminderDao(), database.taskDao())
        val scheduler = ReminderScheduler(appContext)
        val builder = ReminderNotificationBuilder(appContext)

        when (action) {
            ACTION_PRE, ACTION_FIRE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = repository.getById(reminderId)
                        if (reminder == null) {
                            Log.w(TAG, "reminder $reminderId not found")
                            return@launch
                        }
                        if (!reminder.isEnabled) {
                            Log.d(TAG, "reminder $reminderId disabled, skip")
                            return@launch
                        }

                        if (action == ACTION_PRE) {
                            Log.d(TAG, "show PRE")
                            builder.showPre(reminder)
                        } else {
                            Log.d(TAG, "show FIRE")
                            builder.showFire(reminder)
                            repository.rescheduleAfterFire(reminderId)
                            repository.getById(reminderId)?.let { updated ->
                                if (updated.isEnabled) scheduler.schedule(updated)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "error in $action", e)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_DONE -> {
                Log.d(TAG, "DONE id=$reminderId")
                builder.cancelFire(reminderId)
                builder.cancelPre(reminderId)
            }

            ACTION_SNOOZE_FIRE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val reminder = repository.getById(reminderId)
                        if (reminder == null) {
                            Log.w(TAG, "snoozed reminder $reminderId not found")
                            return@launch
                        }

                        Log.d(TAG, "show SNOOZE FIRE")
                        builder.showFire(reminder)
                    } catch (e: Throwable) {
                        Log.e(TAG, "error in $ACTION_SNOOZE_FIRE", e)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_SNOOZE -> {
                Log.d(TAG, "SNOOZE id=$reminderId")
                builder.cancelFire(reminderId)
                builder.cancelPre(reminderId)
                scheduler.scheduleSnooze(reminderId)
            }

            else -> Log.w(TAG, "unknown action: $action")
        }
    }
}
