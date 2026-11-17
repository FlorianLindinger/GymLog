package com.gymlog.app.ui

import android.content.Context
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ManualBodyEntry(
    val weightKg: Double, val fatPercent: Double? = null, val musclePercent: Double? = null,
    val measuredAt: Long = System.currentTimeMillis(), val id: String = UUID.randomUUID().toString(),
    val syncHealthConnect: Boolean = true,
) {
    init {
        require(weightKg.isFinite() && weightKg > 0 && weightKg <= 1000) { "Enter weight between 0 and 1000 kg" }
        require(listOfNotNull(fatPercent, musclePercent).all { it.isFinite() && it in 0.0..100.0 }) { "Percentages must be between 0 and 100" }
        require((fatPercent ?: 0.0) + (musclePercent ?: 0.0) <= 100.0) { "Fat and muscle percentages cannot exceed 100 together" }
    }
}

internal fun manualBodyHealthRecords(entry: ManualBodyEntry): List<Record> {
    val time = Instant.ofEpochMilli(entry.measuredAt)
    val offset = time.atZone(ZoneId.systemDefault()).offset
    return buildList {
        add(WeightRecord(time = time, zoneOffset = offset, weight = Mass.kilograms(entry.weightKg),
            metadata = Metadata.manualEntry(clientRecordId = entry.id + "-weight")))
        entry.fatPercent?.let { add(BodyFatRecord(time = time, zoneOffset = offset, percentage = Percentage(it),
            metadata = Metadata.manualEntry(clientRecordId = entry.id + "-fat"))) }
    }
}

internal class ManualBodyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("manual_body_entries", Context.MODE_PRIVATE)
    fun save(entry: ManualBodyEntry) {
        val rows = JSONArray(prefs.getString("entries", "[]"))
        val next = JSONArray()
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") != entry.id) next.put(rows.getJSONObject(i))
        next.put(JSONObject().put("id", entry.id).put("time", entry.measuredAt).put("weight", entry.weightKg)
            .put("fat", entry.fatPercent).put("muscle", entry.musclePercent))
        check(prefs.edit().putString("entries", next.toString()).commit()) { "Could not save measurement" }
    }
    fun merge(snapshot: BodyDataSnapshot): BodyDataSnapshot {
        val rows = JSONArray(prefs.getString("entries", "[]"))
        val local = buildList {
            for (i in 0 until rows.length()) {
                val e = rows.getJSONObject(i); val time = e.getLong("time"); val weight = e.getDouble("weight")
                add(BodyMeasurement(BodyMetric.WEIGHT, weight, time, context.packageName))
                if (e.has("fat")) {
                    val fat = e.getDouble("fat")
                    add(BodyMeasurement(BodyMetric.FAT_PERCENTAGE, fat, time, context.packageName))
                    add(BodyMeasurement(BodyMetric.FAT_MASS, weight * fat / 100, time, context.packageName))
                }
                if (e.has("muscle")) {
                    val muscle = e.getDouble("muscle")
                    add(BodyMeasurement(BodyMetric.MUSCLE_PERCENTAGE, muscle, time, context.packageName))
                    add(BodyMeasurement(BodyMetric.MUSCLE_MASS, weight * muscle / 100, time, context.packageName))
                }
            }
        }
        return snapshot.copy(measurements = (local + snapshot.measurements)
            .distinctBy { Triple(it.metric, it.measuredAt, it.source) }.sortedBy { it.measuredAt })
    }
}
