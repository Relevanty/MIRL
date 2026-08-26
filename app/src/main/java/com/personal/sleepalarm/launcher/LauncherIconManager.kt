package com.personal.sleepalarm.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Applies aliases without ever leaving the launcher with zero enabled entries. */
class LauncherIconManager(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun activate(requestedId: String) {
        val selected = LauncherIconCatalog.byId(requestedId)
        if (isEnabled(selected) && LauncherIconCatalog.all.asSequence()
                .filterNot { it.id == selected.id }
                .none(::isEnabled)
        ) {
            return
        }

        // The new entry must exist before the old one disappears.
        packageManager.setComponentEnabledSetting(
            component(selected),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        LauncherIconCatalog.all
            .asSequence()
            .filterNot { it.id == selected.id }
            .filter(::isEnabled)
            .forEach { icon ->
                packageManager.setComponentEnabledSetting(
                    component(icon),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
    }

    private fun isEnabled(icon: LauncherIconSpec): Boolean =
        when (packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.enabledByDefault
            else -> false
        }

    private fun component(icon: LauncherIconSpec) =
        ComponentName(appContext.packageName, icon.aliasClassName)
}
