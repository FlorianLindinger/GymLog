package com.gymlog.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.gymlog.app.data.automaticBackupEnabled

class GymBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        if (automaticBackupEnabled(this)) super.onFullBackup(data)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor,
    ) = Unit
}
