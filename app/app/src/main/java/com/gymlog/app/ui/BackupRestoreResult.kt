package com.gymlog.app.ui

/** Returned only after a successful restore; Undo also refreshes the activity's body data. */
class BackupRestoreResult(val undo: suspend () -> Unit)
