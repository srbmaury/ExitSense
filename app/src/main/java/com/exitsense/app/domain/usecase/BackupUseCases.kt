package com.exitsense.app.domain.usecase

import android.net.Uri
import com.exitsense.app.data.backup.BackupRestoreSummary
import com.exitsense.app.data.backup.ProfileBackupManager
import com.exitsense.app.data.backup.RestoreBackupUseCase
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
    private val reminderRepository: ReminderRepository,
    private val backupManager: ProfileBackupManager
) {
    suspend operator fun invoke(uri: Uri): Result<Int> {
        val prefs = dataStore.userPreferences.first()
        val profiles = reminderRepository.getAllProfiles().first()
        return backupManager.exportToUri(prefs, profiles, uri)
    }
}

class ImportBackupUseCase @Inject constructor(
    private val backupManager: ProfileBackupManager,
    private val restoreBackup: RestoreBackupUseCase
) {
    suspend operator fun invoke(
        uri: Uri,
        markSetupComplete: Boolean = false
    ): Result<BackupRestoreSummary> =
        backupManager.importFromUri(uri).mapCatching { result ->
            restoreBackup(result, markSetupComplete = markSetupComplete)
        }
}
