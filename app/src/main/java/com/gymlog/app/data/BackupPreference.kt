package com.gymlog.app.data

import android.app.backup.BackupManager
import android.content.Context

const val GYMLOG_PREFERENCES = "gymlog_settings"
const val AUTOMATIC_BACKUP_ENABLED = "automatic_backup_enabled"

fun automaticBackupEnabled(context: Context): Boolean =
    context.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(AUTOMATIC_BACKUP_ENABLED, true)

fun setAutomaticBackupEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AUTOMATIC_BACKUP_ENABLED, enabled)
        .apply()
    BackupManager(context).dataChanged()
}
