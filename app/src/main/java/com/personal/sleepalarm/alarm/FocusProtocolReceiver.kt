package com.personal.sleepalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.sleepalarm.service.focus.FocusProtocolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Обрабатывает окончание фазы и действия из уведомления. */
class FocusProtocolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, 0)
        if (sessionId <= 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val manager = FocusProtocolManager(context.applicationContext)
                when (intent.action) {
                    ACTION_PHASE_END -> manager.advanceIfDue(sessionId)
                    ACTION_PAUSE -> manager.pauseFocus(sessionId)
                    ACTION_RESUME -> manager.resumeFocus(sessionId)
                    ACTION_REPEAT -> manager.startNextCycle(sessionId)
                    ACTION_CANCEL -> manager.cancel(sessionId, CANCEL_REASON_NOTIFICATION)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "com.personal.sleepalarm.focus.PHASE_END"
        const val ACTION_PAUSE = "com.personal.sleepalarm.focus.PAUSE"
        const val ACTION_RESUME = "com.personal.sleepalarm.focus.RESUME"
        const val ACTION_REPEAT = "com.personal.sleepalarm.focus.REPEAT"
        const val ACTION_CANCEL = "com.personal.sleepalarm.focus.CANCEL"
        const val EXTRA_SESSION_ID = "extra_focus_protocol_session_id"
        private const val CANCEL_REASON_NOTIFICATION = "notification"
    }
}
