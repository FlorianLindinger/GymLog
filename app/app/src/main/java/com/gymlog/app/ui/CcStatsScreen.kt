package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.CcLift
import com.gymlog.app.data.CcMomentum
import com.gymlog.app.data.CcPeriod
import com.gymlog.app.data.CcStats
import com.gymlog.app.data.GymClock
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.buildCcStats
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RISING = Color(0xFF43A047)
private val FALLING = Color(0xFFE53935)
private val FLAT = Color(0xFF9E9E9E)

/** Insight-focused statistics: consistency, strength momentum, records, balance and session profile. */
@Composable
internal fun CcStatsScreen(
    machines: List<MachineSummary>,
    bodyData: BodyDataSnapshot,
    modifier: Modifier = Modifier,
    onMachine: (Long) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(CcPeriod.TWELVE_WEEKS) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    val bodyWeight = remember(bodyData) {
        bodyData.measurements.filter { it.metric == BodyMetric.WEIGHT }.maxByOrNull { it.measuredAt }?.value
    }
    val stats = remember(machines, period, bodyWeight) { buildCcStats(machines, period, GymClock(), bodyWeight) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CcPeriod.entries.forEach { choice ->
                    FilterChip(selected = period == choice, onClick = { period = choice }, label = { Text(choice.label) })
                }
            }
        }
        if (stats.sessions.isEmpty() && stats.lifts.isEmpty()) {
            item { Text("No trainings in this period yet. Log a few sessions and this page fills up.") }
            return@LazyColumn
        }
        item { Headline(stats) }
        item {
            CcCard("Weekly rhythm", onHelp = { help = "Weekly rhythm" to
                "Bars show trainings per Monday-Sunday week (the last bar is the current week). A streak counts consecutive weeks with at least one training; the current week only adds to it, so an untrained Monday does not break it." }) {
                WeekBars(stats)
                Text("Streak ${stats.currentStreakWeeks} wk · longest ${stats.longestStreakWeeks} wk", style = MaterialTheme.typography.bodyMedium)
                WeekdayBars(stats.weekdayCounts)
            }
        }
        item {
            CcCard("Strength momentum", onHelp = { help = "Strength momentum" to
                "For each weight exercise trained in the period, a straight line is fitted through its daily strength score (average estimated 1RM) and expressed as change per 30 days. Above +1 %/month counts as rising, below -1 % as falling. Exercises with under two weeks of history are marked new. 'Next PR' is the smallest load on a 2.5 kg step that beats your best estimate at the reps you used last time." }) {
                if (stats.lifts.isEmpty()) Text("No weight exercises in this period.", style = MaterialTheme.typography.bodySmall)
                MomentumSummary(stats.lifts)
                stats.lifts.forEach { LiftRow(it, onMachine) }
            }
        }
        item {
            CcCard("Recent records", onHelp = { help = "Recent records" to
                "A record is a training day whose strength score beats every earlier day of the same exercise. The first-ever training is not counted as a record." }) {
                if (stats.records.isEmpty()) Text("No new bests in this period.", style = MaterialTheme.typography.bodySmall)
                stats.records.take(10).forEach { record ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(record.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${number(record.estimate)} kg (+${number(record.gainPercent)} %) · ${shortDate(record.date)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            CcCard("Muscle balance (last 4 weeks)", onHelp = { help = "Muscle balance" to
                "Weighted sets per week for each body region over the last 28 days: each set counts with the exercise's region intensity (High 1, Medium 0.5, Low 0.25). Around 10 or more weighted sets per region and week is a common hypertrophy guideline. Regions untrained for 10 days or more are highlighted." }) {
                RegionBars(stats)
                SplitBar(stats)
            }
        }
        item {
            CcCard("Session profile", onHelp = { help = "Session profile" to
                "Duration runs from the first to the last entry of a training day, so time before the first logged set is not included. Rep ranges count weight sets: 1-5 heavy, 6-12 moderate, 13+ light." }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(stats.averageMinutes?.let { "${number(it)} min" } ?: "--", "Avg duration", Modifier.weight(1f))
                    Tile(stats.averageExercises?.let(::number) ?: "--", "Exercises", Modifier.weight(1f))
                    Tile(stats.averageSets?.let(::number) ?: "--", "Sets", Modifier.weight(1f))
                }
                RepMix(stats)
            }
        }
        if (stats.bodyWeight != null && stats.lifts.isNotEmpty()) item {
            CcCard("Relative strength", onHelp = { help = "Relative strength" to
                "Your strongest current lifts (latest strength score) divided by your latest body weight. Useful to see whether strength keeps up while body weight changes; machine numbers are not comparable across brands." }) {
                Text("Body weight ${number(stats.bodyWeight)} kg", style = MaterialTheme.typography.bodySmall)
                stats.lifts.sortedByDescending { it.latest }.take(5).forEach { lift ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(lift.name, Modifier.weight(1f))
                        Text("${String.format(Locale.US, "%.2f", lift.latest / stats.bodyWeight)} × BW")
                    }
                }
            }
        }
    }
    help?.let { (title, text) ->
        AlertDialog(onDismissRequest = { help = null }, title = { Text(title) }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { help = null }) { Text("Close") } })
    }
}

@Composable
private fun Headline(stats: CcStats) {
    val change = stats.sessions.size - stats.previousSessions
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stats.sessions.size.toString(), "Trainings (${if (change >= 0) "+" else ""}$change vs previous)", Modifier.weight(1f))
            Tile(number(stats.trainingsPerWeek), "Per week", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("${stats.currentStreakWeeks} wk", "Week streak", Modifier.weight(1f))
            Tile(stats.records.size.toString(), "New bests", Modifier.weight(1f))
            Tile(stats.totalSets.toString(), "Sets", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekBars(stats: CcStats) {
    val max = (stats.weeks.maxOfOrNull { it.trainings } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        stats.weeks.forEachIndexed { index, week ->
            val current = index == stats.weeks.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (week.trainings > 0) Text(week.trainings.toString(), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.75f * week.trainings / max + 0.02f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (current) 0.5f else 1f), RoundedCornerShape(3.dp)))
            }
        }
    }
    Text("${shortDate(stats.weeks.first().monday)} – this week", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WeekdayBars(counts: List<Int>) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Text("Training days", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEachIndexed { index, day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(counts[index].toFloat() / max + 0.02f)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(3.dp)))
                }
                Text(day, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MomentumSummary(lifts: List<CcLift>) {
    if (lifts.isEmpty()) return
    val counts = CcMomentum.entries.associateWith { m -> lifts.count { it.momentum == m } }
    Text("${counts[CcMomentum.RISING]} rising · ${counts[CcMomentum.FLAT]} flat · ${counts[CcMomentum.FALLING]} falling · ${counts[CcMomentum.NEW]} new",
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LiftRow(lift: CcLift, onMachine: (Long) -> Unit) {
    val color = when (lift.momentum) {
        CcMomentum.RISING -> RISING
        CcMomentum.FALLING -> FALLING
        else -> FLAT
    }
    Column(Modifier.fillMaxWidth().clickable { onMachine(lift.machineId) }.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(4.dp)))
            Text(" ${lift.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(lift.monthlyTrendPercent?.let { "${if (it >= 0) "+" else ""}${number(it)} %/mo" } ?: "new",
                color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text("Now ${number(lift.latest)} kg · best ${number(lift.best)} kg (${if (lift.daysSinceBest == 0L) "today" else "${lift.daysSinceBest} d ago"}) · next PR ${number(lift.nextPrWeight)} kg × ${lift.typicalReps}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RegionBars(stats: CcStats) {
    if (stats.regions.isEmpty()) {
        Text("Add body regions to your exercises to see balance.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = (stats.regions.maxOf { it.weeklySets }).coerceAtLeast(10.0)
    stats.regions.forEach { region ->
        val stale = (region.daysSinceTrained ?: Long.MAX_VALUE) >= 10
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(regionName(region.area), Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = if (stale) FALLING else MaterialTheme.colorScheme.onSurface)
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((region.weeklySets / max).toFloat().coerceIn(0f, 1f)).height(8.dp)
                    .background(Color(region.area.color), RoundedCornerShape(4.dp)))
            }
            Text(" ${number(region.weeklySets)}/wk" + (region.daysSinceTrained?.takeIf { stale }?.let { " · ${it}d" } ?: ""),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SplitBar(stats: CcStats) {
    val split = stats.split
    if (split.total <= 0.0) return
    Text("Push / pull / legs / core (period)", style = MaterialTheme.typography.labelLarge)
    val parts = listOf(split.push to Color(0xFFE6194B), split.pull to Color(0xFF4363D8), split.legs to Color(0xFF3CB44B), split.core to Color(0xFFFFD700))
    Row(Modifier.fillMaxWidth().height(12.dp)) {
        parts.filter { it.first > 0 }.forEach { (value, color) ->
            Box(Modifier.weight((value / split.total).toFloat()).fillMaxHeight().background(color))
        }
    }
    Text(listOf("Push" to split.push, "Pull" to split.pull, "Legs" to split.legs, "Core" to split.core)
        .joinToString(" · ") { (name, value) -> "$name ${(100 * value / split.total).toInt()} %" },
        style = MaterialTheme.typography.labelSmall)
    if (split.pull > 0 && split.push > 0) {
        val ratio = split.push / split.pull
        Text("Push:pull ${String.format(Locale.US, "%.2f", ratio)}" +
            if (ratio > 1.25) " – consider more pulling" else if (ratio < 0.8) " – pulling dominates" else " – balanced",
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RepMix(stats: CcStats) {
    val mix = stats.repMix
    if (mix.total == 0) return
    Text("Rep ranges", style = MaterialTheme.typography.labelLarge)
    listOf("1-5 heavy" to mix.heavy, "6-12 moderate" to mix.moderate, "13+ light" to mix.light).forEach { (label, count) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(count.toFloat() / mix.total).height(8.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp)))
            }
            Text(" ${100 * count / mix.total} %", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CcCard(title: String, onHelp: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onHelp) { Text("?") }
            }
            content()
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun regionName(area: BodyArea): String = area.label.substringBefore(" / ")
private fun number(value: Double): String =
    if (kotlin.math.abs(value - Math.round(value)) < 0.05) Math.round(value).toString() else String.format(Locale.US, "%.1f", value)
private fun shortDate(date: java.time.LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
