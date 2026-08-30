package com.personal.sleepalarm.service.audio

import android.media.AudioAttributes
import android.media.AudioManager

/**
 * One routing policy for every sound MIRL produces.
 *
 * Android has no public flag that can force arbitrary audio through "Total
 * silence". USAGE_ALARM is nevertheless the strongest legitimate routing for
 * this offline alarm/productivity app: it uses the alarm volume stream and is
 * allowed by DND modes that permit alarms.
 */
object AppAudioAttributes {
    const val USAGE = AudioAttributes.USAGE_ALARM
    const val LEGACY_STREAM = AudioManager.STREAM_ALARM

    val sonification: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(USAGE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    val speech: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(USAGE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
