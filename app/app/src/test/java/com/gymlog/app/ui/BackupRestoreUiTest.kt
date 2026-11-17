package com.gymlog.app.ui

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gymlog.app.data.*
import com.gymlog.app.ui.theme.GymLogTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BackupRestoreUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun showApp(repo: GymRepository, version: MutableIntState,
        onImport: ((BackupRestoreResult?) -> Unit) -> Unit) {
        compose.setContent { GymLogTheme {
            GymLogApp(repo, version.intValue, {}, onImport, { _, done -> done(null) }, {}, {}, {},
                BodyDataSnapshot(), "", false, {}, {})
        } }
    }

    private fun chooseBackup() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Menu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Menu").performClick()
        compose.onNodeWithText("Data, Backup & Sync").performScrollTo().performClick()
        compose.onNodeWithText("Restore from Backup").performScrollTo().performClick()
        compose.onNodeWithText("Choose Backup").performClick()
    }

    @Test fun cancelledBackupPickerCannotArmUndoForALaterPlanImport() {
        val repo = GymRepository(context)
        repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val version = mutableIntStateOf(0)
        showApp(repo, version) { complete -> complete(null) }
        chooseBackup()
        repo.importTrainingPlan(repo.exportTrainingPlan())
        compose.runOnIdle { version.intValue++ }
        compose.waitForIdle()
        compose.onNodeWithText("Backup restored").assertDoesNotExist()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        assertEquals(2, repo.loadTrainingPlans().size)
    }

    @Test fun successfulBackupResultOffersOnlyItsOwnUndoAndRestoresOriginalRecords() {
        val repo = GymRepository(context)
        val original = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        repo.addHistoricalSet(original, 10, 40.0, System.currentTimeMillis() - 1000)
        val originalData = repo.exportJson()
        repo.createTrainingPlan("Imported")
        val importedData = repo.exportJson()
        repo.importJson(originalData, preserveIds = true)
        val version = mutableIntStateOf(0)
        var undoCalls = 0
        showApp(repo, version) { complete ->
            val snapshot = repo.exportJson()
            repo.importJson(importedData)
            version.intValue++
            complete(BackupRestoreResult {
                repo.importJson(snapshot, preserveIds = true)
                undoCalls++
            })
        }
        chooseBackup()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, repo.loadTrainingPlans().size)
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { undoCalls == 1 }
        assertEquals(1, repo.loadTrainingPlans().size)
        assertEquals(original, repo.loadMachines().single().machine.id)
        assertEquals(1, repo.loadMachines().single().recentSets.size)
    }

    @Test fun recoveryRequiresASeparateConfirmationBeforeDeletingThePreservedDatabase() {
        context.openOrCreateDatabase(GYMLOG_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY,name TEXT)")
            database.execSQL("INSERT INTO machines VALUES(7,'Keep me')")
            database.version = 8
        }
        val repo = GymRepository(context)
        showApp(repo, mutableIntStateOf(0)) { complete -> complete(null) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("GymLog data could not be opened").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create New Database").performClick()
        compose.onNodeWithText("Delete existing GymLog data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        context.openOrCreateDatabase(GYMLOG_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            assertEquals(8, database.version)
            database.rawQuery("SELECT name FROM machines WHERE id=7", null).use {
                assertTrue(it.moveToFirst()); assertEquals("Keep me", it.getString(0))
            }
        }
        compose.onNodeWithText("Create New Database").performClick()
        compose.onNodeWithText("Delete and Create New").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Build Your Routine").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(repo.loadMachines().isEmpty())
    }
}
