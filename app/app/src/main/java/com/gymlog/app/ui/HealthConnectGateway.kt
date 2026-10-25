package com.gymlog.app.ui

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectGateway(private val context: Context) {
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

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readBodyData(): BodyDataSnapshot {
        val health = client()
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
        return BodyDataSnapshot(result.sortedBy { it.measuredAt }, System.currentTimeMillis())
    }
}
