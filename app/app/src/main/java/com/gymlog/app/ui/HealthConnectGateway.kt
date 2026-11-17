package com.gymlog.app.ui

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectGateway(private val context: Context, private val clientOverride: HealthConnectClient? = null) {
    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )
    }

    private suspend fun <T : Record> HealthConnectClient.readAllRecords(
        recordType: KClass<T>,
        filter: TimeRangeFilter,
    ): List<T> {
        val all = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = filter,
                    ascendingOrder = true,
                    pageSize = 1000,
                    pageToken = pageToken,
                ),
            )
            all += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return all
    }
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    // Change-log position taken just before the last full read. Null forces the next check to report
    // changes, e.g. on first use, after an expired token, or when the token could not be obtained.
    @Volatile private var changesToken: String? = null

    /**
     * Asks Health Connect's change log whether any body record was added, updated or deleted since the
     * last full read. This is a small query, so periodic refreshes can skip re-reading the whole history.
     */
    suspend fun hasChangesSinceLastRead(): Boolean {
        var token = changesToken ?: return true
        val health = client()
        var changed = false
        do {
            val response = health.getChanges(token)
            if (response.changesTokenExpired) {
                changesToken = null
                return true
            }
            if (response.changes.isNotEmpty()) changed = true
            token = response.nextChangesToken
        } while (response.hasMore)
        // When something changed the caller does a full read, which takes a fresh token itself.
        if (!changed) changesToken = token
        return changed
    }

    private fun client(): HealthConnectClient = clientOverride ?: HealthConnectClient.getOrCreate(context)

    fun writePermissions(entry: ManualBodyEntry): Set<String> = buildSet {
        add(HealthPermission.getWritePermission(WeightRecord::class))
        if (entry.fatPercent != null) add(HealthPermission.getWritePermission(BodyFatRecord::class))
    }
    suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()
    suspend fun writeBodyData(entry: ManualBodyEntry) {
        val records = manualBodyHealthRecords(entry)
        val result = client().insertRecords(records)
        check(result.recordIdsList.size == records.size) { "Health Connect did not confirm every record" }
    }

    suspend fun hasPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readBodyData(): BodyDataSnapshot {
        val health = client()
        // Taken before reading so records written during the read show up in the next change check.
        val token = try {
            health.getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class)))
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        val start = Instant.EPOCH
        val end = Instant.now().plusSeconds(24L * 60L * 60L)
        val filter = TimeRangeFilter.between(start, end)
        val weights = health.readAllRecords(WeightRecord::class, filter)
        val fatPercentages = health.readAllRecords(BodyFatRecord::class, filter)
        val leanMasses = health.readAllRecords(LeanBodyMassRecord::class, filter)

        fun source(record: Record): String = record.metadata.dataOrigin.packageName
        val sortedWeights = weights.sortedBy { it.time }
        val weightTimes = sortedWeights.map { it.time.toEpochMilli() }
        fun nearestWeight(time: Instant): WeightRecord? = nearestBodyMeasurementIndex(
            weightTimes, time.toEpochMilli(), 7L * 24L * 60L * 60L * 1000L,
        )?.let { sortedWeights[it] }

        val result = mutableListOf<BodyMeasurement>()
        weights.forEach {
            result += BodyMeasurement(BodyMetric.WEIGHT, it.weight.inKilograms, it.time.toEpochMilli(), source(it))
        }
        fatPercentages.forEach {
            val percentage = it.percentage.value
            result += BodyMeasurement(BodyMetric.FAT_PERCENTAGE, percentage, it.time.toEpochMilli(), source(it))
            nearestWeight(it.time)?.let { weight ->
                result += BodyMeasurement(
                    BodyMetric.FAT_MASS,
                    weight.weight.inKilograms * percentage / 100.0,
                    it.time.toEpochMilli(),
                    source(it),
                )
            }
        }
        leanMasses.forEach {
            val kilograms = it.mass.inKilograms
            result += BodyMeasurement(BodyMetric.MUSCLE_MASS, kilograms, it.time.toEpochMilli(), source(it))
            nearestWeight(it.time)?.takeIf { it.weight.inKilograms > 0.0 }?.let { weight ->
                result += BodyMeasurement(
                    BodyMetric.MUSCLE_PERCENTAGE,
                    kilograms / weight.weight.inKilograms * 100.0,
                    it.time.toEpochMilli(),
                    source(it),
                )
            }
        }
        changesToken = token
        return BodyDataSnapshot(result.sortedBy { it.measuredAt }, System.currentTimeMillis())
    }
}
