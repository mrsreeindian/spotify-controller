package com.spotify.controller.domain

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Manages Do Not Disturb (DND) mode transitions using Android's NotificationManager
 * interruption filters. Requires Notification Policy Access permission from the user.
 */
open class DndManager(private val context: Context?) {

    private val notificationManager: NotificationManager? by lazy {
        context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private var previousInterruptionFilter: Int? = null

    /**
     * Checks whether the user has granted Do Not Disturb (Notification Policy) access.
     */
    open val isDndPermissionGranted: Boolean
        get() = try {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } catch (_: Exception) {
            false
        }

    /**
     * Opens Android System Settings directly to the Notification Policy Access screen.
     */
    open fun openDndSettings() {
        val ctx = context ?: return
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e("DndManager", "Failed to open notification policy access settings", e)
        }
    }

    /**
     * Activates Do Not Disturb (Priority Only).
     * Saves the current interruption filter so it can be restored when the timer ends.
     */
    open fun enableDnd(): Boolean {
        if (!isDndPermissionGranted) return false
        val nm = notificationManager ?: return false
        return try {
            if (previousInterruptionFilter == null) {
                previousInterruptionFilter = nm.currentInterruptionFilter
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        } catch (e: Exception) {
            Log.e("DndManager", "Failed to activate DND", e)
            false
        }
    }

    /**
     * Disables Do Not Disturb and restores the original interruption filter.
     */
    open fun disableDnd(): Boolean {
        if (!isDndPermissionGranted) return false
        val nm = notificationManager ?: return false
        return try {
            val filterToRestore = previousInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            previousInterruptionFilter = null
            nm.setInterruptionFilter(filterToRestore)
            true
        } catch (e: Exception) {
            Log.e("DndManager", "Failed to restore DND filter", e)
            false
        }
    }
}
