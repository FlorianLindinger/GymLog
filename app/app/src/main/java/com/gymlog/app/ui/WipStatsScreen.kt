package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.ChartRange
import com.gymlog.app.data.DashboardExercise
import com.gymlog.app.data.DashboardPeriod
import com.gymlog.app.data.DashboardTraining
import com.gymlog.app.data.GymClock
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.StatsDashboardSnapshot
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.buildStatsDashboard
import com.gymlog.app.data.dashboardPercentChange
import com.gymlog.app.data.estimatedOneRepMax
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun WipStatsScreen(
    machines: List<MachineSummary>,
    hiddenIds: Set<Long>,
    planName: String,
    progressionSettings: ProgressionSettings,
    onHistory: () -> Unit,
    onMachine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("stats_dashboard_settings", 0) }
    var range by remember { mutableStateOf(ChartRange.fromStorage(preferences.getString("range", "3M"))) }
    var customStart by remember { mutableStateOf(preferences.getLong("start", 0).takeIf { it > 0 }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("end", 0).takeIf { it > 0 }) }
    var includeHidden by remember { mutableStateOf(preferences.getBoolean("include_hidden", true)) }
    var weeklyGoal by remember { mutableStateOf(preferences.getInt("weekly_goal", 3).coerceIn(1, 7)) }
    var showCustom by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }
    var showMetricHelp by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DashboardTraining?>(null) }
    var chart by rememberSaveable { mutableStateOf("Sessions") }
    var ranking by rememberSaveable { mutableStateOf("Progress") }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var allRecords by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val clock = GymClock(zoneId = zone)
    val today = trainingDate(clock.now(), zone)
    val filteredMachines = remember(machines, includeHidden, hiddenIds) {
        machines.filter { includeHidden || it.machine.id !in hiddenIds }
    }
    val period = remember(range, customStart, customEnd, filteredMachines, today, zone) {
        val first = when (range) {
            ChartRange.ALL -> filteredMachines.flatMap { it.recentSets }.minOfOrNull { trainingDate(it.performedAt, zone) } ?: today
            ChartRange.CUSTOM -> customStart?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today.minusDays(29)
            else -> today.minusDays(((chartWindowDuration(range) ?: 30L * 86_400_000) / 86_400_000).coerceAtLeast(1) - 1)
        }.coerceAtMost(today)
        val last = if (range == ChartRange.CUSTOM) customEnd?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.coerceIn(first, today) ?: today else today
        DashboardPeriod(first, last)
    }
    val snapshot = remember(filteredMachines, period, progressionSettings, today) {
        buildStatsDashboard(filteredMachines, period, progressionSettings, clock)
    }
    val window = remember(period, zone) { ChartWindow(null,
        startMillis = dashboardTimestamp(period.first, zone, 6),
        endMillis = dashboardTimestamp(period.last.plusDays(1), zone, 6) - 1,
        key = period,
    ) }
    val ranked = remember(snapshot.exercises, ranking, query) {
        val matches = snapshot.exercises.filter { it.summary.machine.name.contains(query, ignoreCase = true) }
        when (ranking) {
            "Warnings" -> matches.filter { it.warning != null }.sortedBy { it.summary.machine.name }
            "Frequency" -> matches.sortedWith(compareBy<DashboardExercise> { it.days.size }.thenByDescending { it.daysSinceLast ?: Long.MAX_VALUE })
            "Name" -> matches.filter { it.days.isNotEmpty() }.sortedBy { it.summary.machine.name.lowercase(Locale.ROOT) }
            else -> matches.filter { it.days.isNotEmpty() }.sortedByDescending { it.changePercent ?: Double.NEGATIVE_INFINITY }
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(planName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your training, in context", style = MaterialTheme.typography.titleSmall)
                    Text("${dashboardDate(period.first)} - ${dashboardDate(period.last)}", style = MaterialTheme.typography.bodySmall)
                    Text("Compared with ${dashboardDate(period.previous().first)} - ${dashboardDate(period.previous().last)} (${period.days} days each)", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = onHistory) { Text("History") }
                        TextButton(onClick = { showMethods = true }) { Text("How measured ?") }
                    }
                }
            }
        }
        item {
            DashboardCard("Date range") {
                ChartRangeControls(range, false, {
                    range = it; preferences.edit().putString("range", it.storageValue).apply()
                }, { showCustom = true }, {}, showTrendControl = false)
                if (hiddenIds.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Include hidden exercises", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(includeHidden, {
                        includeHidden = it; preferences.edit().putBoolean("include_hidden", it).apply()
                    })
                }
                Text("Every total and chart below follows this range. Current warning badges are marked Now.", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (snapshot.current.sessions == 0) item {
            DashboardCard("No training in this range") {
                Text("Choose a longer interval or log your first entry. Earlier comparison data and untrained exercises can still appear below.")
            }
        }
        item { DashboardOverview(snapshot) }
        item {
            DashboardCard("Consistency") {
                val fullWeeks = snapshot.weeks.filter { it.complete }
                val met = fullWeeks.count { it.sessions >= weeklyGoal }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Goal: $weeklyGoal trainings / week", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showGoal = true }) { Text("Change") }
                }
                Text(if (fullWeeks.isEmpty()) "No complete calendar weeks in this range yet." else
                    "Goal met in $met of ${fullWeeks.size} complete weeks (${dashboardNumber(100.0 * met / fullWeeks.size)}%).")
                snapshot.weeks.takeLast(6).forEach { week ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(week.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)), Modifier.weight(.8f), style = MaterialTheme.typography.bodySmall)
                        DashboardBar(week.sessions.toFloat() / weeklyGoal, MaterialTheme.colorScheme.primary, Modifier.weight(1.5f))
                        Text("${week.sessions}/$weeklyGoal" + if (!week.complete) " *" else "", Modifier.weight(.6f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("* Partial or ongoing weeks are shown but excluded from goal success. A training is a day with at least one recorded entry.", style = MaterialTheme.typography.labelSmall)
            }
        }
        item { DashboardCalendar(snapshot, onDay = { selectedDay = it }) }
        item {
            DashboardCard("Training trends") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Sessions", "Volume", "Time", "Cardio").forEach { choice ->
                        FilterChip(chart == choice, { chart = choice }, label = { Text(choice) })
                    }
                }
                val points = when (chart) {
                    "Volume" -> snapshot.current.trainings.map { TimeValue(dashboardTimestamp(it.date, zone), it.weightVolume) }
                    "Time" -> snapshot.current.trainings.mapNotNull { day -> day.recordedMinutes?.let { TimeValue(dashboardTimestamp(day.date, zone), it) } }
                    "Cardio" -> snapshot.current.trainings.map { TimeValue(dashboardTimestamp(it.date, zone), it.cardioMinutes) }
                    else -> snapshot.weeks.map { TimeValue(dashboardTimestamp(it.date.coerceIn(period.first, period.last), zone), it.sessions.toDouble()) }
                }
                Text(when (chart) {
                    "Volume" -> "Total kg x reps per training day; a workload measure, separate from 1RM strength."
                    "Time" -> "Minutes between first and last recorded entry. Unknown durations are omitted."
                    "Cardio" -> "Recorded minutes in level, watts and duration exercises."
                    else -> "Training days per Monday-Sunday week. Boundary weeks may be partial."
                }, style = MaterialTheme.typography.bodySmall)
                DashboardPlot(points, window)
            }
        }
        item {
            DashboardCard("Personal records") {
                if (snapshot.records.isEmpty()) Text("No new recorded bests against earlier history in this range. First-ever entries establish a baseline.")
                else {
                    Text("${snapshot.records.size} exercises exceeded their best training before this range.", style = MaterialTheme.typography.bodySmall)
                    (if (allRecords) snapshot.records else snapshot.records.take(6)).forEach { exercise ->
                        Row(Modifier.fillMaxWidth().clickable { onMachine(exercise.summary.machine.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(exercise.summary.machine.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${exercise.label} ${dashboardNumber(exercise.best!!)} ${exercise.unit}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (snapshot.records.size > 6) TextButton(onClick = { allRecords = !allRecords }) { Text(if (allRecords) "Show fewer" else "Show all records") }
                }
            }
        }
        item {
            DashboardCard("Exercise explorer") {
                Text("Strength uses best-set 1RM, watts use peak power, levels use peak level, and duration uses total recorded minutes per day.", style = MaterialTheme.typography.bodySmall)
                Text("Change compares the latest training with the previous period's latest, or the first training in this period if no earlier baseline exists. Tap an exercise to expand its chart.", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(query, { query = it }, label = { Text("Find an exercise") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Progress", "Warnings", "Frequency", "Name").forEach { choice ->
                        FilterChip(ranking == choice, { ranking = choice }, label = { Text(choice) })
                    }
                }
                if (ranking == "Warnings") Text("Now: saved warning rules use all available history and today's date, independently of the displayed range.", style = MaterialTheme.typography.labelSmall)
                if (ranking == "Frequency") Text("Least trained in this range first. Last-trained age refers to today.", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (ranked.isEmpty()) item { Text("No matching exercises.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(ranked, key = { "exercise-${it.summary.machine.id}" }) { exercise ->
            DashboardExerciseCard(exercise, exercise.summary.machine.id == expandedId, window, zone,
                onExpand = { expandedId = if (expandedId == exercise.summary.machine.id) null else exercise.summary.machine.id },
                onOpen = { onMachine(exercise.summary.machine.id) }, onMetricHelp = { showMetricHelp = true })
        }
        item {
            DashboardCard("Body-region workload") {
                Text("Recorded weight sets weighted by the exercise's current region intensities: High 1, Medium 0.5, Low 0.25. This is a workload estimate, not a recovery score.", style = MaterialTheme.typography.bodySmall)
                if (snapshot.regions.isEmpty()) Text("Add body regions to weight exercises to see their workload here.")
                val maximum = snapshot.regions.maxOfOrNull { it.weightedSets } ?: 1.0
                snapshot.regions.forEach { region ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row {
                            Text(region.area.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("${dashboardNumber(region.weightedSets)} sets", style = MaterialTheme.typography.labelSmall)
                        }
                        DashboardBar((region.weightedSets / maximum).toFloat(), Color(region.color), Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            DashboardCard("Data context") {
                Text("${snapshot.missingProfiles} trained exercises have no body-region profile.")
                Text("${snapshot.higherRepSets} weight sets have more than 10 reps; 1RM estimates are less informative for higher-rep sets.")
                val unknown = snapshot.current.sessions - snapshot.current.knownDurationCount
                Text("$unknown trainings have no measurable span between entries. They count as trainings; duration averages exclude them.")
                Text("Level numbers depend on the machine. Their changes are shown as level steps rather than percentages.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showMethods = true }) { Text("How these numbers are calculated") }
            }
        }
    }
    if (showCustom) CustomBodyRangeDialog(
        maxTimestamp = clock.now(), initialStart = dashboardTimestamp(period.first, zone, 0), initialEnd = dashboardTimestamp(period.last, zone, 0),
        onDismiss = { showCustom = false }, onSave = { first, last ->
            customStart = first; customEnd = last; range = ChartRange.CUSTOM
            preferences.edit().putString("range", "CUSTOM").putLong("start", first).putLong("end", last).apply()
            showCustom = false
        })
    if (showGoal) DashboardGoalDialog(weeklyGoal, { showGoal = false }) {
        weeklyGoal = it; preferences.edit().putInt("weekly_goal", it).apply(); showGoal = false
    }
    if (showMetricHelp) TrainingMetricExplanationDialog { showMetricHelp = false }
    selectedDay?.let { day -> DashboardDayDialog(day, { selectedDay = null }) { id -> selectedDay = null; onMachine(id) } }
    if (showMethods) AlertDialog(
        onDismissRequest = { showMethods = false }, title = { Text("How WIP stats works") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Training days run from 06:00 to 05:59. The comparison period contains the same number of calendar days and ends just before the selected range. Future entries are excluded. All data belongs to the active plan; hidden exercises follow the switch above.")
            Text("Sessions count distinct training days. Sets and volume include weight exercises only; cardio minutes are recorded duration in other modes. Recorded span is first-to-last-entry time, so it omits time before the first entry and is unknown when timestamps do not differ.")
            Text("Strength change uses best-set Wathan 1RM for each exercise. The plan summary is the median percentage change of comparable weight exercises, rather than adding kilograms across unrelated lifts. No percentage is invented for zero or missing baselines. Level progress uses absolute level steps.")
            Text("Exercise baselines use the previous period's last training when available; otherwise the first of at least two trainings in this range. Personal records compare the period's best with all earlier history. New exercises establish a baseline rather than claiming a record.")
            Text("Weekly targets count complete Monday-Sunday weeks only. The calendar shows the last 12 weeks of the selected range; shading represents number of logged entries. Body-region totals weight recorded sets by current profiles and do not measure fatigue or recovery.")
            Text("Warning badges describe the current situation using each exercise's saved global/custom warning settings, including its Volume/1RM choice. The 1RM strength explorer and workload-volume chart let you inspect both dimensions separately.")
        } }, confirmButton = { TextButton(onClick = { showMethods = false }) { Text("Close") } })
}

@Composable
private fun DashboardOverview(snapshot: StatsDashboardSnapshot) {
    val current = snapshot.current
    val previous = snapshot.previous
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTile(current.sessions.toString(), "Trainings", dashboardComparison(current.sessions.toDouble(), previous.sessions.toDouble()), Modifier.weight(1f))
            DashboardTile(current.weightSets.toString(), "Weight sets", dashboardComparison(current.weightSets.toDouble(), previous.weightSets.toDouble()), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTile(snapshot.medianStrengthChange?.let { "${dashboardSigned(it)}%" } ?: "--", "Median 1RM change", "${snapshot.exercises.count { it.mode == TrackingMode.WEIGHT && it.changePercent != null }} comparable exercises", Modifier.weight(1f))
            DashboardTile(snapshot.records.size.toString(), "New exercise bests", "Against earlier history", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTile("${dashboardNumber(current.volume / 1000)} t", "Weight x reps", dashboardComparison(current.volume / 1000, previous.volume / 1000, "t"), Modifier.weight(1f))
            DashboardTile("${dashboardNumber(current.cardioMinutes)}m", "Cardio minutes", dashboardComparison(current.cardioMinutes, previous.cardioMinutes, "m"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTile("${dashboardNumber(current.recordedMinutes / 60)}h", "Recorded training span", dashboardComparison(current.recordedMinutes / 60, previous.recordedMinutes / 60, "h"), Modifier.weight(1f))
            DashboardTile(current.averageMinutes?.let { "${dashboardNumber(it)}m" } ?: "--", "Average recorded span", "${current.knownDurationCount} trainings with known span", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardTile(current.exercises.toString(), "Exercises trained", dashboardComparison(current.exercises.toDouble(), previous.exercises.toDouble()), Modifier.weight(1f))
            DashboardTile(current.reps.toString(), "Weight reps", dashboardComparison(current.reps.toDouble(), previous.reps.toDouble()), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DashboardTile(value: String, label: String, comparison: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AutoFitText(value, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), Modifier.fillMaxWidth(), MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(comparison, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardBar(fraction: Float, color: Color, modifier: Modifier) {
    Box(modifier.height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun DashboardPlot(points: List<TimeValue>, window: ChartWindow) {
    if (points.isEmpty()) Text("Not enough recorded data for this chart.", style = MaterialTheme.typography.bodySmall)
    else TimeWindowChart(listOf(TimeSeries(points, MaterialTheme.colorScheme.primary)), window,
        height = 150.dp, compactYAxis = true, bodyCalendarLabels = true)
}

@Composable
private fun DashboardCalendar(snapshot: StatsDashboardSnapshot, onDay: (DashboardTraining) -> Unit) {
    val byDate = remember(snapshot.current) { snapshot.current.trainings.associateBy { it.date } }
    val first = maxOf(snapshot.period.first, snapshot.period.last.minusWeeks(11))
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = snapshot.period.last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
    val maximum = byDate.values.maxOfOrNull { it.entries.size }?.coerceAtLeast(1) ?: 1
    DashboardCard("Training calendar") {
        Text("${dashboardDate(maxOf(first, snapshot.period.first))} - ${dashboardDate(snapshot.period.last)}. Tap a training day for details.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                week.forEach { date ->
                    val day = byDate[date]
                    val inPeriod = snapshot.period.contains(date)
                    val fill = if (!inPeriod) Color.Transparent else if (day == null) MaterialTheme.colorScheme.surfaceVariant else
                        MaterialTheme.colorScheme.primary.copy(alpha = .25f + .75f * day.entries.size / maximum)
                    Box(Modifier.weight(1f).height(30.dp).background(fill, RoundedCornerShape(5.dp))
                        .semantics { contentDescription = "${dashboardDate(date)}: ${day?.entries?.size ?: 0} entries" }
                        .clickable(enabled = day != null) { day?.let(onDay) }, contentAlignment = Alignment.Center) {
                        if (inPeriod) Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                            color = if (day != null && day.entries.size.toFloat() / maximum > .5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Text("Darker days have more logged entries, not necessarily higher intensity. The calendar shows up to the last 12 weeks of the range.", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DashboardExerciseCard(
    exercise: DashboardExercise, expanded: Boolean, window: ChartWindow, zone: ZoneId,
    onExpand: () -> Unit, onOpen: () -> Unit, onMetricHelp: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.summary.machine.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (expanded) "Close" else "Explore", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (exercise.latest == null) Text("Not trained in this range.", style = MaterialTheme.typography.bodySmall)
            else {
                val baseline = exercise.baseline
                val change = if (exercise.mode == TrackingMode.STRENGTH) baseline?.let {
                    "${dashboardSigned(exercise.latest!! - it.metric)} level steps"
                } else exercise.changePercent?.let { "${dashboardSigned(it)}%" }
                Text("${exercise.label} ${dashboardNumber(exercise.latest!!)} ${exercise.unit}" + (change?.let { "  |  $it" } ?: "  |  No comparison yet"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (baseline != null) Text("Baseline ${dashboardDate(baseline.date)}: ${dashboardNumber(baseline.metric)} ${exercise.unit}", style = MaterialTheme.typography.labelSmall)
            }
            Text("${exercise.days.size} trainings in range" + (exercise.averageGap?.let { " | every ${dashboardNumber(it)} days" } ?: "") +
                (exercise.daysSinceLast?.let { " | last $it days ago" } ?: " | never trained"), style = MaterialTheme.typography.labelSmall)
            exercise.warning?.let { Text("Now: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            if (expanded) {
                if (exercise.mode == TrackingMode.WEIGHT) TextButton(onClick = onMetricHelp) { Text("1RM: what does it mean?", style = MaterialTheme.typography.labelSmall) }
                DashboardPlot(exercise.days.map { TimeValue(dashboardTimestamp(it.date, zone), it.metric) }, window)
                if (exercise.mode == TrackingMode.WEIGHT) Text("Period workload: ${dashboardNumber(exercise.days.sumOf { it.volume } / 1000)} t (kg x reps)", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpen) { Text("Open exercise") }
            }
        }
    }
}

@Composable
private fun DashboardGoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    val parsed = value.toIntOrNull()?.takeIf { it in 1..7 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Weekly training goal") },
        text = { OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("Training days per week (1-7)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = parsed == null) },
        confirmButton = { Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DashboardDayDialog(day: DashboardTraining, onDismiss: () -> Unit, onMachine: (Long) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(dashboardDate(day.date)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${day.entries.size} entries | ${day.entries.map { it.machineId }.distinct().size} exercises")
            Text(day.recordedMinutes?.let { "Recorded span ${dashboardNumber(it)}m" } ?: "Recorded span unknown")
            day.entries.groupBy { it.machineId }.forEach { (id, entries) ->
                Column(Modifier.fillMaxWidth().clickable { onMachine(id) }.padding(vertical = 4.dp)) {
                    Text(entries.first().machineName, fontWeight = FontWeight.SemiBold)
                    val mode = entries.first().trackingMode
                    Text(if (mode == TrackingMode.WEIGHT) "${entries.size} sets | 1RM ${dashboardNumber(entries.maxOf { estimatedOneRepMax(it.weight, it.reps) })} kg | ${dashboardNumber(entries.sumOf { it.volume })} kg x reps"
                    else "${dashboardNumber(entries.sumOf { it.weight })}m" + if (mode.usesResistanceLevel()) " | peak ${entries.maxOf { it.reps }} ${if (mode == TrackingMode.WATTS) "W" else "Lv."}" else "",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

private fun TrackingMode.usesResistanceLevel(): Boolean = this == TrackingMode.STRENGTH || this == TrackingMode.WATTS
private fun dashboardTimestamp(date: LocalDate, zone: ZoneId, hour: Int = 12): Long = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
private fun dashboardDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH))
private fun dashboardNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
private fun dashboardSigned(value: Double): String = (if (value > 0) "+" else "") + dashboardNumber(value)
private fun dashboardComparison(current: Double, previous: Double, unit: String = ""): String {
    val difference = current - previous
    val percent = dashboardPercentChange(current, previous)
    return if (difference == 0.0) "Unchanged from previous period" else "${dashboardSigned(difference)}$unit" +
        (percent?.let { " (${dashboardSigned(it)}%)" } ?: " vs ${dashboardNumber(previous)}$unit previously")
}
