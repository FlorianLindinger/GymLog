package com.gymlog.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gymlog.app.data.*
import com.gymlog.app.ui.theme.GymLogTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class NewActionsUiTest {
    @Test fun fatigueBoundaryEditorRejectsInvalidOrderAndSavesChosenThresholds() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>())
        val current = androidx.compose.runtime.mutableStateOf(FatigueBands())
        compose.setContent { GymLogTheme { FatigueBandSettings(current.value) {
            repo.saveFatigueBands(it); current.value = it
        } } }
        compose.onNodeWithText("Orange (%)").performTextReplacement("10")
        compose.onNodeWithText("Save colors").assertIsNotEnabled()
        assertEquals(FatigueBands(), repo.loadFatigueBands())
        compose.onNodeWithText("Yellow (%)").performTextReplacement("10")
        compose.onNodeWithText("Orange (%)").performTextReplacement("40")
        compose.onNodeWithText("Red (%)").performTextReplacement("80")
        compose.onNodeWithText("Save colors").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(FatigueBands(10, 40, 80), repo.loadFatigueBands()) }
        compose.onNodeWithText("Default colors").performClick()
        compose.onNodeWithText("Save colors").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(FatigueBands(), repo.loadFatigueBands()) }
    }

    @get:Rule val compose = createComposeRule()
    @Test fun compactRegionEditorSavesCustomPercentage() {
        var saved: List<AreaAssignment>? = null
        compose.setContent { GymLogTheme { MuscleProfileDialog("Custom exercise", emptyList(), onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithContentDescription("Core percentage").performScrollTo().performTextInput("150")
        compose.onNodeWithText("Save").performClick()
        assertEquals(1.5, saved!!.single { it.area == BodyArea.CORE }.intensity, 0.0)
    }
    @Test fun bannerCreatesAnotherPlanAndSwitchesDirectlyBack() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>())
        val original = repo.loadTrainingPlans().single()
        compose.setContent { GymLogTheme { GymLogApp(repo, 0, {}, {}, { _, done -> done(null) }, {}, {}, {}, BodyDataSnapshot(), "", false, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText(original.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(original.name).performClick()
        compose.onNodeWithText("Hide plans").assertDoesNotExist()
        compose.onNodeWithText("Switch training plan").assertDoesNotExist()
        compose.onNodeWithText("Add another training plan").performScrollTo().performClick()
        compose.onNodeWithText("Plan Name").performTextInput("Second routine")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { repo.loadTrainingPlans().size == 2 }
        assertEquals("Second routine", repo.loadTrainingPlans().first { it.id == repo.activeTrainingPlanId() }.name)
        compose.waitUntil(10000) { compose.onAllNodesWithText("Second routine").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Second routine").performClick()
        compose.onNodeWithText(original.name).performScrollTo().performClick()
        compose.waitUntil(10000) { repo.activeTrainingPlanId() == original.id }
    }

    @Test fun bodyPlotExpandsAndIndividualVisibilityChoicePersists() {
        val now = System.currentTimeMillis()
        val data = BodyDataSnapshot(listOf(BodyMeasurement(BodyMetric.WEIGHT, 80.0, now, "Test")))
        compose.setContent { GymLogTheme { BodyOverviewScreen(data, {}, "", false, onSync = {}, onAutoSync = {}, onGym = {}) } }
        compose.onNodeWithText("Weight (80 kg)").performClick()
        compose.onNodeWithText("Scroll for plot settings. Changes preview above; Save applies them.").performScrollTo().assertIsDisplayed()
        val force = hasText("Force show") and hasAnyAncestor(hasTestTag("visibility_showTargetLower"))
        compose.onNode(force).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Save").performClick()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("body_graph_settings", 0)
        assertEquals("SHOW", prefs.getString("WEIGHT_visibility_showTargetLower", null))
        compose.onNodeWithText("Weight (80 kg)").performClick()
        compose.onNode(force).performScrollTo().assertIsSelected()
    }

    @Test fun exerciseShowsLatestEntryAbovePlotAndFullHistoryBelow() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>())
        val id = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val start = System.currentTimeMillis() - 42L * 86_400_000
        repeat(4) { repo.addHistoricalSet(id, 10, 40.0, start + it * 86_400_000) }
        val summary = repo.loadMachines().single()
        compose.setContent { GymLogTheme { DetailScreen(summary, emptyMap(), emptyMap(), repository = repo,
            onAreaColor = { _, _ -> }, onChanged = {}, onTrainingLogged = {}, onEditDetails = {},
            isHidden = false, onDeleteSets = {}, onDeleteExercise = {}, onDeleted = {}) } }
        compose.onNodeWithText("weeks no kg increase", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Last:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Recent Entries").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tap plot to enlarge and adjust").performScrollTo().performClick()
        compose.onNodeWithText("Plot settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
    }
    @Test fun addWeightIsAvailableOnlyInBody() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>())
        compose.setContent { GymLogTheme { GymLogApp(repo, 0, {}, {}, { _, done -> done(null) }, {}, {}, {}, BodyDataSnapshot(), "", false, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add weight").assertDoesNotExist()
        compose.onNodeWithText("Body").performClick()
        compose.onNodeWithText("Add weight").assertIsDisplayed().performClick()
        compose.onNode(hasText("Weight (kg)") and hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Gym").performClick()
        compose.onNodeWithText("Add weight").assertDoesNotExist()
    }
    @Test fun deleteLastTrainingButtonConfirmsCancelsDeletesAndSupportsUndo() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>(),
            GymClock(Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC))
        val id = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        repo.addHistoricalSet(id, 10, 40.0, Instant.parse("2026-11-01T12:00:00Z").toEpochMilli())
        compose.setContent { GymLogTheme { GymLogApp(repo, 0, {}, {}, { _, done -> done(null) }, {}, {}, {}, BodyDataSnapshot(), "", false, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Menu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Menu").performClick()
        compose.onNodeWithText("Delete Last Training").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, repo.loadMachines().single().recentSets.size)
        compose.onNodeWithText("Delete Last Training").performScrollTo().performClick()
        compose.onNodeWithText("Delete Training").performClick()
        compose.waitUntil(10000) { repo.loadMachines().single().recentSets.isEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { repo.loadMachines().single().recentSets.size == 1 }
    }
    @Test fun planSelectorFileButtonsInvokeCorrectActions() {
        var downloads = 0; var imports = 0; var closed = 0
        val plan = TrainingPlan(1, "Routine", 0, "", false)
        compose.setContent { GymLogTheme { TrainingHeaderDialog(plan, listOf(plan), { downloads++ }, { imports++ }, { closed++ }, { _, _, _ -> }, {}) } }
        compose.onNodeWithText("Download plan").performScrollTo().performClick(); assertEquals(1, downloads)
        compose.onNodeWithText("Load file").performScrollTo().performClick(); assertEquals(1, imports); assertEquals(1, closed)
    }
    @Test fun bodyDialogSavesOptionalPercentagesAndShowsSyncFailure() {
        var saved: ManualBodyEntry? = null
        compose.setContent { GymLogTheme { AddBodyMeasurementDialog({}) { value, complete -> saved = value; complete("Permission denied") } } }
        compose.onNodeWithText("Weight (kg)").performTextInput("80")
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextClearance()
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextInput("2026-01-02")
        compose.onNodeWithText("Body fat (%) · optional").performTextInput("20")
        compose.onNodeWithText("Muscle (%) · optional").performTextInput("40")
        compose.onNodeWithText("Save").performClick()
        assertEquals(java.time.LocalDate.of(2026, 1, 2), java.time.Instant.ofEpochMilli(saved!!.measuredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        assertEquals(80.0, saved!!.weightKg, 0.0); assertEquals(20.0, saved!!.fatPercent!!, 0.0); assertEquals(40.0, saved!!.musclePercent!!, 0.0)
        compose.onNodeWithText("Permission denied").performScrollTo().assertIsDisplayed()
    }
}
