package com.personal.sleepalarm.util

import android.media.RingtoneManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingtonePickerHelperTest {
    @Test
    fun defaultAliases_areMatchedOnlyToTheirRequestedType() {
        val alarm = "content://settings/system/alarm_alert"
        val notification = "content://settings/system/notification_sound"

        assertTrue(RingtonePickerHelper.isDefaultAlias(alarm, RingtoneManager.TYPE_ALARM))
        assertTrue(
            RingtonePickerHelper.isDefaultAlias(notification, RingtoneManager.TYPE_NOTIFICATION)
        )
        assertFalse(RingtonePickerHelper.isDefaultAlias(alarm, RingtoneManager.TYPE_NOTIFICATION))
        assertFalse(
            RingtonePickerHelper.isDefaultAlias(
                "content://com.android.thememanager/ringtones/aurora",
                RingtoneManager.TYPE_ALARM
            )
        )
        assertTrue(
            RingtonePickerHelper.isDefaultAlias(
                notification,
                RingtoneManager.TYPE_ALL
            )
        )
    }
}
