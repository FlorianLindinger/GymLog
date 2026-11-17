package com.gymlog.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.clickable
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
    @Test fun bodyIntervalPickerShowsNominalPresetsAndSavesTheSelectedRange() {
        compose.setContent { GymLogTheme {
            BodyOverviewScreen(BodyDataSnapshot(), {}, "", false, onSync = {}, onAutoSync = {}, onGym = {})
        } }
        listOf("1wk", "2wk", "3wk", "1mon", "2mon", "3mon", "4mon", "6mon", "1yr", "2yr", "All", "Custom")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        listOf("8wk", "12mon", "1yr 11mon", "2yr 11mon", "4yr 11mon", "3yr", "5yr")
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("2mon").performClick()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("body_graph_settings", 0)
        compose.runOnIdle { assertEquals("2M", prefs.getString("window", null)) }
        compose.onNodeWithText("1yr").performClick()
        compose.runOnIdle { assertEquals("1Y", prefs.getString("window", null)) }
        compose.onNodeWithText("All").performClick()
        compose.runOnIdle { assertEquals("ALL", prefs.getString("window", null)) }
    }

    @Test fun weightWarningEditorUsesDaysAndDefaultsToSixty() {
        var saved: ProgressionSettings? = null
        compose.setContent { GymLogTheme {
            ProgressionSettingsDialog(ProgressionSettings(), onDismiss = {}, onSave = { saved = it })
        } }
        val field = compose.onNode(hasText("Days Without Weight Increase") and hasSetTextAction())
        field.performScrollTo().assertTextContains("60")
        field.performTextReplacement("75")
        compose.onNodeWithText("Save").performClick()
        assertEquals(75, saved?.staleDays)
    }

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
    @Test fun previousDayFatigueEditorRejectsOutOfRangeAndSaves() {
        var saved: PriorDayFatigue? = null
        compose.setContent { GymLogTheme { PriorDayFatigueSettings(PriorDayFatigue()) { saved = it } } }
        compose.onNodeWithText("Save days").assertIsNotEnabled()
        compose.onNodeWithText("Yesterday (%)").performTextReplacement("150")
        compose.onNodeWithText("Save days").assertIsNotEnabled()
        compose.onNodeWithText("Yesterday (%)").performTextReplacement("30")
        compose.onNodeWithText("3 days (%)").performTextReplacement("0")
        compose.onNodeWithText("Save days").assertIsEnabled().performClick()
        assertEquals(PriorDayFatigue(30, 20, 0), saved)
    }
    @Test fun tappingAChartPointShowsItsValueWhileOtherTapsReachTheCard() {
        var cardClicks = 0
        val start = java.time.Instant.parse("2026-11-02T18:30:00Z").toEpochMilli()
        val points = listOf(TimeValue(start, 10.0), TimeValue(start + 10 * 86_400_000L, 17.3))
        compose.setContent { GymLogTheme {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.clickable { cardClicks++ }) {
                TimeWindowChart(listOf(TimeSeries(points, androidx.compose.ui.graphics.Color.Blue)), ChartWindow(null, key = "test"))
            }
        } }
        val plot = compose.onNodeWithTag("chart_plot", useUnmergedTree = true)
        // Halfway between the two points: nothing to select, so the card receives the tap.
        plot.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, cardClicks) }
        compose.onNodeWithText("17.3", substring = true, useUnmergedTree = true).assertDoesNotExist()
        val range = chartAxisRange(listOf(10.0, 17.3))
        plot.performTouchInput {
            val y = height - 6f - ((17.3 - range.min) / range.span).toFloat() * (height - 12f)
            click(androidx.compose.ui.geometry.Offset(width - 1f, y))
        }
        compose.onNodeWithText("17.3", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(formatChartPointTime(start + 10 * 86_400_000L), useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(1, cardClicks) }
    }
    @Test fun compactRegionEditorSavesCustomPercentage() {
        var saved: List<AreaAssignment>? = null
        compose.setContent { GymLogTheme { MuscleProfileDialog("Custom exercise", emptyList(), onDismiss = {}, onSave = { saved = it }) } }
        compose.onNodeWithContentDescription("Core percentage").performScrollTo().performTextInput("150")
        compose.onNodeWithText("Save").performClick()
        assertEquals(1.5, saved!!.single { it.area == BodyArea.CORE }.intensity, 0.0)
    }
    @Test fun clearingEveryWheelUsesConfirmedDeleteInsteadOfSilentlyReplacingTheEntry() {
        var added: List<Int>? = null
        var deleted = 0
        compose.setContent { GymLogTheme { QuickRepDialog(List(5) { 0 }, listOf(10, 10), isChange = true,
            onDismiss = {}, onAdd = { added = it }, onDelete = { deleted++ }) } }
        compose.onNode(hasText("Change Sets") and hasClickAction()).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, deleted)
            assertNull(added)
        }
    }
    @Test fun emptyNewEntryCannotBeSaved() {
        compose.setContent { GymLogTheme { QuickRepDialog(List(5) { 0 }, emptyList(), onDismiss = {}, onAdd = { fail("No sets") }) } }
        compose.onNode(hasText("Save Sets") and hasClickAction()).assertIsNotEnabled()
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
        compose.onNodeWithText("Tap a section to change it. The preview updates live; Save applies.").assertExists()
        val force = hasText("Show") and hasAnyAncestor(hasTestTag("visibility_showTargetLower"))
        // Sections other than Time range start collapsed and show a summary instead.
        compose.onNode(force).assertDoesNotExist()
        compose.onNodeWithText("No target").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Target").performScrollTo().performClick()
        compose.onNode(force).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Save").performClick()
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("body_graph_settings", 0)
        assertEquals("SHOW", prefs.getString("WEIGHT_visibility_showTargetLower", null))
        compose.onNodeWithText("Weight (80 kg)").performClick()
        compose.onNodeWithText("Target").performScrollTo().performClick()
        compose.onNode(force).performScrollTo().assertIsSelected()
    }

    @Test fun exerciseShowsLatestEntryAbovePlotAndFullHistoryBelow() {
        val repo = GymRepository(ApplicationProvider.getApplicationContext<Context>())
        val id = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val start = System.currentTimeMillis() - 70L * 86_400_000
        repeat(4) { repo.addHistoricalSet(id, 10, 40.0, start + it * 86_400_000) }
        val summary = repo.loadMachines().single()
        compose.setContent { GymLogTheme { DetailScreen(summary, emptyMap(), emptyMap(), repository = repo,
            onAreaColor = { _, _ -> }, onChanged = {}, onTrainingLogged = {}, onEditDetails = {},
            isHidden = false, onDeleteSets = {}, onDeleteExercise = {}, onDeleted = {}) } }
        compose.onNodeWithText("days no kg increase", substring = true).assertIsDisplayed()
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
        // The IO deletion finishes before the coroutine publishes the snackbar to Compose.
        compose.waitUntil(10000) { compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
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
