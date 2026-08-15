package com.personal.sleepalarm.ui.misc

/**
 * Экраны, открываемые из popup «Разное».
 */
sealed class MiscScreen {
    object Library : MiscScreen()
    object Reminders : MiscScreen()
    object DDay : MiscScreen()
    object Assistant : MiscScreen()
    object Briefing : MiscScreen()
}