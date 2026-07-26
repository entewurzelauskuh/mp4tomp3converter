package io.github.entewurzelauskuh.mp4tomp3.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Controls whether the app appears in the system share sheet, by enabling/disabling the
 * `.ShareTarget` activity-alias that carries the SEND intent filters. The component's enabled state
 * is itself persistent (stored by the package manager), so it is the single source of truth — no
 * separate setting needs saving. Enabled by default (matches the manifest `android:enabled="true"`).
 */
object ShareSheet {
    // The alias's fully-qualified name is namespace-derived, NOT applicationId-derived — use a
    // constant so it stays correct even if a build variant ever adds an applicationIdSuffix.
    private const val SHARE_TARGET = "io.github.entewurzelauskuh.mp4tomp3.ShareTarget"

    private fun component(context: Context) = ComponentName(context.packageName, SHARE_TARGET)

    /** True unless the user has explicitly disabled the share-sheet entry. */
    fun isEnabled(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(component(context)) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Enable or disable the app's share-sheet entry (persists across restarts). */
    fun setEnabled(context: Context, enabled: Boolean) {
        val state =
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        context.packageManager.setComponentEnabledSetting(component(context), state, PackageManager.DONT_KILL_APP)
    }
}
