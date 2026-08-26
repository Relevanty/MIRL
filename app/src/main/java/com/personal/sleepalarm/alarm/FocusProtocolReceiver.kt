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
                    ACTION_PHASE_END -> manager.advanceIfDue(
                        sessionId = sessionId,
                        expectedPhase = intent.getStringExtra(EXTRA_EXPECTED_PHASE)
                            ?.let { runCatching { com.personal.sleepalarm.domain.model.FocusProtocolPhase.valueOf(it) }.getOrNull() },
                        expectedEnd = intent.getLongExtra(EXTRA_EXPECTED_END, 0L)
                            .takeIf { it > 0L }
                    )
                    ACTION_PAUSE -> manager.pauseFocus(sessionId)
                    ACTION_RESUME -> manager.resumeFocus(sessionId)
                    ACTION_REPEAT -> manager.startNextCycle(sessionId)
                    ACTION_SKIP_RESET -> manager.skipReset(sessionId)
                    ACTION_START_FOCUS -> manager.startFocus(sessionId)
                    ACTION_FINISH_FOCUS -> manager.finishFocus(sessionId)
                    ACTION_FINISH_RECOVERY -> manager.finishRecovery(sessionId)
                    ACTION_MARK_DISTRACTION -> manager.incrementDistraction(sessionId)
                    ACTION_FINISH_BLOCK -> manager.finishBlock(sessionId)
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
        const val ACTION_SKIP_RESET = "com.personal.sleepalarm.focus.SKIP_RESET"
        const val ACTION_START_FOCUS = "com.personal.sleepalarm.focus.START_FOCUS"
        const val ACTION_FINISH_FOCUS = "com.personal.sleepalarm.focus.FINISH_FOCUS"
        const val ACTION_FINISH_RECOVERY = "com.personal.sleepalarm.focus.FINISH_RECOVERY"
        const val ACTION_MARK_DISTRACTION = "com.personal.sleepalarm.focus.MARK_DISTRACTION"
        const val ACTION_FINISH_BLOCK = "com.personal.sleepalarm.focus.FINISH_BLOCK"
        const val ACTION_CANCEL = "com.personal.sleepalarm.focus.CANCEL"
        const val EXTRA_SESSION_ID = "extra_focus_protocol_session_id"
        const val EXTRA_EXPECTED_PHASE = "extra_focus_protocol_expected_phase"
        const val EXTRA_EXPECTED_END = "extra_focus_protocol_expected_end"
        private const val CANCEL_REASON_NOTIFICATION = "notification"
    }
}
